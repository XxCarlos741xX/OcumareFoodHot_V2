package com.project.ocumare_food.inventario;

import com.project.ocumare_food.catalogo.CatalogoService;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.inventario.dto.InventarioDto.*;
import com.project.ocumare_food.inventario.model.*;
import com.project.ocumare_food.inventario.repository.*;
import com.project.ocumare_food.pedidos.ComandaSolicitada;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.usuarios.Usuarios;
import jakarta.persistence.EntityManager;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Inventario de insumos con recetas.
 *
 * - Cuando un pedido entra a cocina (o se le agregan platos), se descuentan los insumos de la receta de cada plato.
 * - Si un plato enviado se cancela, o el pedido se anula mientras está en cocina, los insumos se devuelven.
 *   (Si en realidad ya se preparó y se botó, se registra una MERMA.)
 * - El stock puede quedar negativo: el inventario nunca frena una venta, solo avisa.
 * - Si a un plato ya no le alcanza un insumo para una porción, se marca agotado en la carta; cuando vuelve a
 *   alcanzar, se pone disponible otra vez (solo los que agotó el inventario, nunca los que agotó el gerente).
 * - Los platos sin receta no se tocan.
 */
@Service
public class InventarioService {

    static final int MAX_MOVIMIENTOS = 200;

    private final InsumoRepository insumos;
    private final RecetaRepository recetas;
    private final MovimientoRepository movimientos;
    private final AgotadoRepository agotados;
    private final CatalogoService catalogo;
    private final Clock reloj;
    private final EntityManager em;
    private final Usuarios usuarios;

    public InventarioService(InsumoRepository insumos, RecetaRepository recetas, MovimientoRepository movimientos,
                             AgotadoRepository agotados, CatalogoService catalogo, Clock reloj, EntityManager em,
                             Usuarios usuarios) {
        this.em = em;
        this.usuarios = usuarios;
        this.insumos = insumos;
        this.recetas = recetas;
        this.movimientos = movimientos;
        this.agotados = agotados;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Nombre de quien registra (igual que en la bitácora de pedidos). */
    private String nombre(UsuarioActual actor) {
        if (actor == null || actor.id() == null) return null;
        return usuarios.referencia(actor.id()).getNombre();
    }

    private static BigDecimal cant(BigDecimal n) {
        return n == null ? BigDecimal.ZERO : n.setScale(3, RoundingMode.HALF_UP);
    }

    // ===================== Pedidos: descuento automático =====================

    /** Corre en la misma transacción que el pedido: si el pedido no se guarda, el descuento tampoco. */
    @EventListener
    public void alSolicitarComanda(ComandaSolicitada e) {
        int signo;
        TipoMovimiento tipo;
        switch (e.tipo()) {
            case NUEVA, ADICIONAL -> { signo = -1; tipo = TipoMovimiento.VENTA; }
            case CANCELADO, ANULADO -> { signo = 1; tipo = TipoMovimiento.DEVOLUCION; }
            default -> { return; }   // la reimpresión no se prepara de nuevo
        }
        Map<Long, Integer> platos = new LinkedHashMap<>();
        Map<Long, String> nombres = new HashMap<>();
        for (ComandaSolicitada.Linea l : e.lineas()) {
            if (l.productoId() == null) continue;
            platos.merge(l.productoId(), l.cantidad(), Integer::sum);
            nombres.putIfAbsent(l.productoId(), l.nombre());
        }
        if (platos.isEmpty()) return;

        // Total por insumo y de qué platos sale, para la nota del movimiento.
        Map<Long, BigDecimal> porInsumo = new LinkedHashMap<>();
        Map<Long, Insumo> insumoPorId = new HashMap<>();
        Map<Long, Set<String>> origen = new HashMap<>();
        for (RecetaLinea r : recetas.findByProductoIdIn(platos.keySet())) {
            if (!r.getInsumo().isActivo()) continue;
            BigDecimal total = r.getCantidad().multiply(BigDecimal.valueOf(platos.get(r.getProductoId())));
            porInsumo.merge(r.getInsumo().getId(), total, BigDecimal::add);
            insumoPorId.put(r.getInsumo().getId(), r.getInsumo());
            origen.computeIfAbsent(r.getInsumo().getId(), k -> new LinkedHashSet<>())
                    .add(platos.get(r.getProductoId()) + " x " + nombres.get(r.getProductoId()));
        }
        if (porInsumo.isEmpty()) return;

        String accion = switch (e.tipo()) {
            case CANCELADO -> "Cancelado en pedido #";
            case ANULADO -> "Pedido anulado #";
            default -> "Pedido #";
        };
        String quien = e.solicitadoPor() == null ? "Web" : e.solicitadoPor();
        porInsumo.forEach((id, total) -> {
            String nota = accion + e.numeroPedido() + ": " + String.join(", ", origen.get(id));
            mover(insumoPorId.get(id), tipo, cant(total).multiply(BigDecimal.valueOf(signo)), null,
                    e.pedidoId(), e.numeroPedido(), nota, quien);
        });
        revisarAgotados(porInsumo.keySet());
    }

    /** Suma o resta al stock (de forma atómica) y deja el movimiento en el historial. */
    private MovimientoInventario mover(Insumo i, TipoMovimiento tipo, BigDecimal cantidad, BigDecimal costo,
                                       Long pedidoId, Integer numeroPedido, String nota, String usuario) {
        insumos.sumarStock(i.getId(), cantidad, ahora());
        if (em.contains(i)) em.refresh(i);   // la suma fue directa en la base: que el objeto en memoria no quede viejo
        BigDecimal resultante = insumos.stockDe(i.getId());
        return movimientos.save(MovimientoInventario.builder()
                .insumo(i).tipo(tipo).cantidad(cantidad).stockResultante(resultante).costoUnitarioUsd(costo)
                .pedidoId(pedidoId).numeroPedido(numeroPedido)
                .nota(nota == null ? null : nota.length() > 200 ? nota.substring(0, 199) + "…" : nota)
                .usuario(usuario).creadoEn(ahora()).build());
    }

    /**
     * Agota en la carta los platos a los que ya no les alcanza un insumo para una porción,
     * y vuelve a poner disponibles los que el inventario había agotado y ya alcanzan.
     */
    private void revisarAgotados(Collection<Long> insumoIds) {
        Set<Long> productos = recetas.findByInsumoIdIn(insumoIds).stream().map(RecetaLinea::getProductoId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        revisarProductos(productos);
    }

    private void revisarProductos(Collection<Long> productoIds) {
        if (productoIds.isEmpty()) return;
        Map<Long, List<RecetaLinea>> porProducto = recetas.findByProductoIdIn(productoIds).stream()
                .collect(Collectors.groupingBy(RecetaLinea::getProductoId));
        Map<Long, BigDecimal> stock = new HashMap<>();
        Map<Long, Producto> carta = catalogo.productosPorId(productoIds).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        for (Long productoId : productoIds) {
            Producto p = carta.get(productoId);
            if (p == null) continue;
            String falta = porProducto.getOrDefault(productoId, List.of()).stream()
                    .filter(r -> r.getInsumo().isActivo())
                    .filter(r -> stock.computeIfAbsent(r.getInsumo().getId(), insumos::stockDe).compareTo(r.getCantidad()) < 0)
                    .map(r -> r.getInsumo().getNombre()).findFirst().orElse(null);
            Optional<AgotadoPorInventario> marca = agotados.findById(productoId);
            if (falta != null && marca.isEmpty() && p.isDisponible()) {
                catalogo.cambiarDisponible(productoId, false);
                agotados.save(AgotadoPorInventario.builder().productoId(productoId).insumo(falta).desde(ahora()).build());
            } else if (falta == null && marca.isPresent()) {
                agotados.delete(marca.get());
                if (!p.isDisponible()) catalogo.cambiarDisponible(productoId, true);
            }
        }
    }

    // ===================== Insumos =====================

    @Transactional(readOnly = true)
    public List<InsumoResponse> listarInsumos() {
        Map<Long, Long> usos = recetas.findAll().stream()
                .collect(Collectors.groupingBy(r -> r.getInsumo().getId(), Collectors.counting()));
        return insumos.findAllByOrderByActivoDescNombreAsc().stream()
                .map(i -> InsumoResponse.de(i, usos.getOrDefault(i.getId(), 0L).intValue())).toList();
    }

    @Transactional
    public InsumoResponse crearInsumo(InsumoRequest req, UsuarioActual actor) {
        String nombre = req.nombre().trim();
        if (insumos.existsByNombreIgnoreCase(nombre)) throw ErrorNegocio.conflicto("Ya existe un insumo con ese nombre");
        BigDecimal inicial = cant(req.stockInicial());
        Insumo i = insumos.save(Insumo.builder()
                .nombre(nombre).unidad(req.unidad())
                .stockActual(inicial).stockMinimo(cant(req.stockMinimo()))
                .costoUnitarioUsd(req.costoUnitarioUsd())
                .actualizadoEn(ahora()).build());
        movimientos.save(MovimientoInventario.builder().insumo(i).tipo(TipoMovimiento.AJUSTE).cantidad(inicial)
                .stockResultante(inicial).costoUnitarioUsd(req.costoUnitarioUsd()).nota("Stock inicial")
                .usuario(nombre(actor)).creadoEn(ahora()).build());
        return InsumoResponse.de(i, 0);
    }

    @Transactional
    public InsumoResponse editarInsumo(Long id, InsumoRequest req) {
        Insumo i = insumo(id);
        String nombre = req.nombre().trim();
        if (insumos.existsByNombreIgnoreCaseAndIdNot(nombre, id)) throw ErrorNegocio.conflicto("Ya existe un insumo con ese nombre");
        boolean estabaActivo = i.isActivo();
        i.setNombre(nombre);
        i.setUnidad(req.unidad());
        if (req.stockMinimo() != null) i.setStockMinimo(cant(req.stockMinimo()));
        i.setCostoUnitarioUsd(req.costoUnitarioUsd());
        if (req.activo() != null) i.setActivo(req.activo());
        insumos.flush();
        if (estabaActivo != i.isActivo()) revisarAgotados(List.of(id));
        return InsumoResponse.de(i, recetas.findByInsumoIdIn(List.of(id)).size());
    }

    private Insumo insumo(Long id) {
        return insumos.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Insumo no encontrado"));
    }

    // ===================== Movimientos manuales =====================

    /** Compra (ENTRADA), pérdida (MERMA) o conteo físico (AJUSTE, solo gerencia). */
    @Transactional
    public MovimientoResponse registrar(MovimientoRequest req, UsuarioActual actor) {
        Insumo i = insumo(req.insumoId());
        if (!i.isActivo()) throw ErrorNegocio.invalido("El insumo está desactivado");
        BigDecimal cantidad = cant(req.cantidad());
        BigDecimal delta;
        String nota = limpio(req.nota());
        switch (req.tipo()) {
            case ENTRADA -> {
                if (cantidad.signum() <= 0) throw ErrorNegocio.invalido("La cantidad que entró debe ser mayor que 0");
                delta = cantidad;
            }
            case MERMA -> {
                if (cantidad.signum() <= 0) throw ErrorNegocio.invalido("La cantidad perdida debe ser mayor que 0");
                if (nota == null) throw ErrorNegocio.invalido("Escribe qué pasó (se dañó, se cayó, venció…)");
                delta = cantidad.negate();
            }
            case AJUSTE -> {
                if (!actor.esGerencia()) throw ErrorNegocio.prohibido("Solo el gerente puede ajustar el inventario por conteo");
                delta = cantidad.subtract(insumos.stockDe(i.getId()));
                nota = "Conteo físico: " + cantidad.stripTrailingZeros().toPlainString() + (nota == null ? "" : ". " + nota);
            }
            default -> throw ErrorNegocio.invalido("Las ventas y devoluciones las registra el sistema solo");
        }
        MovimientoInventario m = mover(i, req.tipo(), delta,
                req.tipo() == TipoMovimiento.ENTRADA ? req.costoUnitarioUsd() : null, null, null, nota, nombre(actor));
        // Después de mover (que refresca el insumo): la compra deja el costo de la última compra.
        if (req.tipo() == TipoMovimiento.ENTRADA && req.costoUnitarioUsd() != null) i.setCostoUnitarioUsd(req.costoUnitarioUsd());
        revisarAgotados(List.of(i.getId()));
        return MovimientoResponse.de(m);
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> movimientos(Long insumoId, Integer limite) {
        int n = limite == null ? 50 : Math.max(1, Math.min(limite, MAX_MOVIMIENTOS));
        List<MovimientoInventario> lista = insumoId == null
                ? movimientos.findAllByOrderByIdDesc(PageRequest.of(0, n))
                : movimientos.findByInsumoIdOrderByIdDesc(insumoId, PageRequest.of(0, n));
        return lista.stream().map(MovimientoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public List<MovimientoResponse> movimientosDePedido(Long pedidoId) {
        return movimientos.findByPedidoIdOrderByIdAsc(pedidoId).stream().map(MovimientoResponse::de).toList();
    }

    // ===================== Recetas =====================

    /** Todos los platos activos de la carta con su receta (vacía si no tiene). */
    @Transactional(readOnly = true)
    public List<RecetaResponse> recetas() {
        List<Producto> carta = catalogo.productosActivos();
        Map<Long, List<RecetaLinea>> porProducto = recetas.findAllByOrderByProductoIdAscIdAsc().stream()
                .collect(Collectors.groupingBy(RecetaLinea::getProductoId));
        Set<Long> marcados = agotados.findAll().stream().map(AgotadoPorInventario::getProductoId).collect(Collectors.toSet());
        return carta.stream().map(p -> receta(p, porProducto.getOrDefault(p.getId(), List.of()), marcados.contains(p.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public RecetaResponse receta(Long productoId) {
        Producto p = producto(productoId);
        return receta(p, recetas.findByProductoIdOrderByIdAsc(productoId), agotados.existsById(productoId));
    }

    @Transactional
    public RecetaResponse guardarReceta(Long productoId, RecetaRequest req) {
        Producto p = producto(productoId);
        Set<Long> vistos = new HashSet<>();
        for (LineaRecetaRequest l : req.lineas()) {
            if (!vistos.add(l.insumoId())) throw ErrorNegocio.invalido("Un insumo está repetido en la receta");
        }
        Map<Long, Insumo> porId = insumos.findAllById(vistos).stream().collect(Collectors.toMap(Insumo::getId, Function.identity()));
        for (Long id : vistos) {
            Insumo i = porId.get(id);
            if (i == null) throw ErrorNegocio.noEncontrado("Insumo no encontrado");
            if (!i.isActivo()) throw ErrorNegocio.invalido(i.getNombre() + " está desactivado");
        }
        recetas.deleteByProductoId(productoId);
        recetas.flush();
        for (LineaRecetaRequest l : req.lineas()) {
            recetas.save(RecetaLinea.builder().productoId(productoId).insumo(porId.get(l.insumoId())).cantidad(cant(l.cantidad())).build());
        }
        recetas.flush();
        revisarProductos(List.of(productoId));
        return receta(p, recetas.findByProductoIdOrderByIdAsc(productoId), agotados.existsById(productoId));
    }

    private Producto producto(Long id) {
        return catalogo.productosPorId(List.of(id)).stream().findFirst()
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Producto no encontrado"));
    }

    private RecetaResponse receta(Producto p, List<RecetaLinea> lineas, boolean agotadoPorInventario) {
        BigDecimal costo = BigDecimal.ZERO;
        boolean costoCompleto = true;
        Integer alcanza = null;
        List<LineaRecetaResponse> salida = new ArrayList<>();
        for (RecetaLinea r : lineas) {
            Insumo i = r.getInsumo();
            BigDecimal c = i.getCostoUnitarioUsd() == null ? null
                    : i.getCostoUnitarioUsd().multiply(r.getCantidad()).setScale(4, RoundingMode.HALF_UP);
            if (c == null) costoCompleto = false; else costo = costo.add(c);
            if (i.isActivo()) {
                int n = i.getStockActual().signum() <= 0 ? 0
                        : i.getStockActual().divide(r.getCantidad(), 0, RoundingMode.FLOOR).intValue();
                alcanza = alcanza == null ? n : Math.min(alcanza, n);
            }
            salida.add(new LineaRecetaResponse(i.getId(), i.getNombre(), i.getUnidad(), r.getCantidad(), c, i.getStockActual()));
        }
        return new RecetaResponse(p.getId(), p.getNombre(), p.getPrecioUsd(), salida,
                lineas.isEmpty() || !costoCompleto ? null : costo.setScale(2, RoundingMode.HALF_UP), alcanza, agotadoPorInventario);
    }

    // ===================== Alertas =====================

    @Transactional(readOnly = true)
    public AlertasInventario alertas() {
        List<InsumoResponse> bajos = insumos.bajoMinimo().stream().map(i -> InsumoResponse.de(i, 0)).toList();
        List<Long> ids = agotados.findAll().stream().map(AgotadoPorInventario::getProductoId).toList();
        List<String> platos = ids.isEmpty() ? List.of()
                : catalogo.productosPorId(ids).stream().map(Producto::getNombre).sorted().toList();
        return new AlertasInventario(bajos, platos);
    }
}
