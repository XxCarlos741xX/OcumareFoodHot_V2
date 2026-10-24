package com.project.ocumare_food.pedidos.service;


import com.project.ocumare_food.pedidos.dto.PedidoDto.*;

import static com.project.ocumare_food.pedidos.dto.PedidoDto.MAX_LINEAS;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.pedidos.model.DetallePedido;
import com.project.ocumare_food.pedidos.model.Pago;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.moneda.model.Moneda;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.pedidos.model.TipoPedido;
import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.pedidos.repository.PedidoRepository;
import com.project.ocumare_food.catalogo.CatalogoService;
import com.project.ocumare_food.moneda.TasaService;
import com.project.ocumare_food.pedidos.PedidoWebRecibido;
import com.project.ocumare_food.salon.MesaService;
import com.project.ocumare_food.turnos.TurnoService;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.usuarios.Usuarios;
import com.project.ocumare_food.compartido.Dinero;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Flujo completo de un pedido:
 *
 *   ABIERTO → EN_PREPARACION → LISTO → ENTREGADO → PAGADO (cerrado)
 *                     cualquiera (sin pagos) → ANULADO (solo gerencia)
 *
 * - El pedido se asocia al mesero del token y al turno abierto.
 * - Los precios SIEMPRE se toman de la base de datos, nunca del cliente.
 * - La tasa no es obligatoria (ver TasaService).
 * - Un pedido pasa a PAGADO solo cuando está ENTREGADO y su saldo es 0, sin importar el orden.
 */
@Service
public class PedidoService {

    /** Margen por redondeo al convertir Bs → USD (medio centavo por cada lado no alcanza con pagos mixtos). */
    static final BigDecimal TOLERANCIA_USD = new BigDecimal("0.05");

    private static final Set<EstadoPedido> EDITABLES = EnumSet.of(
            EstadoPedido.ABIERTO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);

    private static final Map<EstadoPedido, EstadoPedido> SIGUIENTE = Map.of(
            EstadoPedido.ABIERTO, EstadoPedido.EN_PREPARACION,
            EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO,
            EstadoPedido.LISTO, EstadoPedido.ENTREGADO);

    private final PedidoRepository pedidos;
    private final CatalogoService catalogo;
    private final MesaService mesas;
    private final TurnoService turnos;
    private final Usuarios usuarios;
    private final TasaService tasas;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    public PedidoService(PedidoRepository pedidos, CatalogoService catalogo, MesaService mesas,
                         TurnoService turnos, Usuarios usuarios, TasaService tasas, Clock reloj,
                         ApplicationEventPublisher eventos) {
        this.pedidos = pedidos;
        this.catalogo = catalogo;
        this.mesas = mesas;
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.tasas = tasas;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ===================== Crear =====================

    @Transactional
    public PedidoResponse crear(CrearPedidoRequest req, UsuarioActual actor) {
        if (req.tipo() == TipoPedido.WEB) {
            throw ErrorNegocio.invalido("Los pedidos WEB solo entran por la página");
        }
        Mesa mesa = null;
        if (req.tipo() == TipoPedido.MESA) {
            if (req.mesaId() == null) throw ErrorNegocio.invalido("Indica la mesa");
            mesa = mesas.buscar(req.mesaId()).orElseThrow(() -> ErrorNegocio.noEncontrado("Mesa no encontrada"));
            if (!mesa.isActiva()) throw ErrorNegocio.invalido("La mesa está desactivada");
        } else if (req.mesaId() != null) {
            throw ErrorNegocio.invalido("Solo los pedidos tipo MESA llevan mesa");
        }
        if (req.tipo() == TipoPedido.DELIVERY
                && (limpio(req.direccionEntrega()) == null || limpio(req.clienteTelefono()) == null)) {
            throw ErrorNegocio.invalido("Un delivery necesita dirección y teléfono del cliente");
        }

        Turno turno = turnoAbiertoBloqueado("No hay un turno abierto. Pide al gerente que abra el turno.");
        if (mesa != null && !pedidos.findByMesaIdAndEstadoIn(mesa.getId(), PedidoRepository.ESTADOS_ABIERTOS).isEmpty()) {
            throw ErrorNegocio.conflicto("La mesa " + mesa.getNumero() + " ya tiene un pedido abierto; agrégale productos a ese");
        }

        Pedido p = nuevoPedido(turno, req.tipo());
        p.setMesa(mesa);
        p.setMesero(usuarios.referencia(actor.id()));
        p.setClienteNombre(limpio(req.clienteNombre()));
        p.setClienteTelefono(limpio(req.clienteTelefono()));
        p.setDireccionEntrega(req.tipo() == TipoPedido.DELIVERY ? limpio(req.direccionEntrega()) : null);
        p.setCostoDeliveryUsd(req.tipo() == TipoPedido.DELIVERY && req.costoDeliveryUsd() != null
                ? Dinero.monto(req.costoDeliveryUsd()) : BigDecimal.ZERO);
        p.setNotas(limpio(req.notas()));
        agregarItems(p, req.items());
        return PedidoResponse.de(pedidos.save(p));
    }

    /** Pedido desde la página web. Idempotente: la misma claveExterna devuelve el mismo pedido. */
    @Transactional
    public PedidoWebResponse crearWeb(PedidoWebRequest req) {
        if (req.website() != null && !req.website().isBlank()) {
            throw ErrorNegocio.invalido("Pedido rechazado");          // bot que llenó el campo trampa
        }
        if (req.entrega() == EntregaWeb.DELIVERY && limpio(req.direccionEntrega()) == null) {
            throw ErrorNegocio.invalido("Indica la dirección de entrega");
        }
        Optional<Pedido> previo = pedidos.findByClaveExterna(req.claveExterna());
        if (previo.isPresent()) return PedidoWebResponse.de(previo.get());

        Turno turno = turnoAbiertoBloqueado(null);
        // Revisar de nuevo con el turno bloqueado: un reintento simultáneo pudo entrar justo antes.
        previo = pedidos.findByClaveExterna(req.claveExterna());
        if (previo.isPresent()) return PedidoWebResponse.de(previo.get());

        Pedido p = nuevoPedido(turno, TipoPedido.WEB);
        p.setClaveExterna(req.claveExterna());
        p.setClienteNombre(limpio(req.clienteNombre()));
        p.setClienteTelefono(limpio(req.clienteTelefono()));
        p.setDireccionEntrega(req.entrega() == EntregaWeb.DELIVERY ? limpio(req.direccionEntrega()) : null);
        StringBuilder notas = new StringBuilder(req.entrega() == EntregaWeb.RETIRO ? "[Retira en el local]" : "[Delivery]");
        if (limpio(req.pago()) != null) notas.append(" Pago: ").append(limpio(req.pago())).append('.');
        if (limpio(req.notas()) != null) notas.append(' ').append(limpio(req.notas()));
        p.setNotas(notas.length() > 500 ? notas.substring(0, 500) : notas.toString());
        agregarItems(p, req.items());
        Pedido guardado = pedidos.save(p);
        eventos.publishEvent(new PedidoWebRecibido(guardado.getId()));
        return PedidoWebResponse.de(guardado);
    }

    @Transactional(readOnly = true)
    public PedidoWebResponse estadoWeb(String claveExterna) {
        return pedidos.findByClaveExterna(claveExterna).map(PedidoWebResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Pedido no encontrado"));
    }

    private Turno turnoAbiertoBloqueado(String mensajeSiNoHay) {
        return turnos.bloquearTurnoAbierto().orElseThrow(() -> mensajeSiNoHay == null
                ? new ErrorNegocio(HttpStatus.SERVICE_UNAVAILABLE, "El local no está recibiendo pedidos en este momento")
                : ErrorNegocio.conflicto(mensajeSiNoHay));
    }

    /** Número diario = máximo del día + 1. Es seguro porque el turno abierto está bloqueado. */
    private Pedido nuevoPedido(Turno turno, TipoPedido tipo) {
        return Pedido.builder()
                .turno(turno)
                .fechaOperativa(turno.getFechaOperativa())
                .numeroDiario(pedidos.maxNumeroDiario(turno.getFechaOperativa()) + 1)
                .tipo(tipo)
                .tasaBsPorUsd(tasas.tasaVigente().orElse(null))
                .build();
    }

    private void agregarItems(Pedido p, List<ItemRequest> items) {
        if (p.getDetalles().size() + items.size() > MAX_LINEAS) {
            throw ErrorNegocio.invalido("El pedido no puede tener más de " + MAX_LINEAS + " líneas");
        }
        Map<Long, Producto> porId = new HashMap<>();
        catalogo.productosPorId(items.stream().map(ItemRequest::productoId).toList())
                .forEach(pr -> porId.put(pr.getId(), pr));
        for (ItemRequest it : items) {
            Producto pr = porId.get(it.productoId());
            if (pr == null || !pr.isActivo()) {
                throw ErrorNegocio.invalido("El producto " + it.productoId() + " no existe");
            }
            if (!pr.isDisponible()) {
                throw ErrorNegocio.conflicto("\"" + pr.getNombre() + "\" está agotado");
            }
            p.agregarDetalle(DetallePedido.de(pr, it.cantidad(), limpio(it.notas())));
        }
    }

    // ===================== Consultar =====================

    /** Pedidos del turno abierto (o de un turno dado). soloAbiertos = los que siguen en curso. */
    @Transactional(readOnly = true)
    public List<PedidoResumen> listar(Long turnoId, boolean soloAbiertos) {
        Long id = turnoId != null ? turnoId : turnos.turnoAbierto()
                .map(Turno::getId)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No hay ningún turno abierto"));
        List<Pedido> lista = soloAbiertos
                ? pedidos.findByTurnoIdAndEstadoInOrderByIdDesc(id, PedidoRepository.ESTADOS_ABIERTOS)
                : pedidos.findByTurnoIdOrderByIdDesc(id);
        return lista.stream().map(PedidoResumen::de).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id) {
        return PedidoResponse.de(pedido(id));
    }

    private Pedido pedido(Long id) {
        return pedidos.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Pedido no encontrado"));
    }

    private Pedido pedidoEditable(Long id) {
        Pedido p = pedido(id);
        if (!EDITABLES.contains(p.getEstado())) {
            throw ErrorNegocio.conflicto("El pedido #" + p.getNumeroDiario() + " está " + p.getEstado() + " y ya no se puede modificar");
        }
        return p;
    }

    // ===================== Modificar =====================

    @Transactional
    public PedidoResponse agregarItems(Long id, List<ItemRequest> items) {
        Pedido p = pedidoEditable(id);
        agregarItems(p, items);
        pedidos.flush();                      // para que las líneas nuevas tengan id en la respuesta
        return PedidoResponse.de(p);
    }

    @Transactional
    public PedidoResponse quitarItem(Long id, Long detalleId) {
        Pedido p = pedidoEditable(id);
        DetallePedido d = p.getDetalles().stream().filter(x -> x.getId().equals(detalleId)).findFirst()
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Ese producto no está en el pedido"));
        if (p.getDetalles().size() == 1) {
            throw ErrorNegocio.invalido("No se puede dejar el pedido vacío; si no va, anúlalo");
        }
        p.quitarDetalle(d);
        if (p.getTotalUsd().compareTo(p.getPagadoUsd()) < 0) {
            throw ErrorNegocio.conflicto("No se puede quitar: lo ya pagado quedaría por encima del total");
        }
        return PedidoResponse.de(p);
    }

    /** Avanza el estado un paso (ABIERTO → EN_PREPARACION → LISTO → ENTREGADO). */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevo) {
        Pedido p = pedido(id);
        if (nuevo == EstadoPedido.ANULADO) throw ErrorNegocio.invalido("Para anular usa la opción Anular");
        if (nuevo == EstadoPedido.PAGADO) throw ErrorNegocio.invalido("El pedido se cierra solo al quedar entregado y pagado");
        EstadoPedido esperado = SIGUIENTE.get(p.getEstado());
        if (esperado == null || nuevo != esperado) {
            throw ErrorNegocio.conflicto("No se puede pasar de " + p.getEstado() + " a " + nuevo);
        }
        p.setEstado(nuevo);
        cerrarSiCorresponde(p);
        return PedidoResponse.de(p);
    }

    @Transactional
    public PedidoResponse anular(Long id, String motivo, UsuarioActual actor) {
        if (actor.rol() == Rol.MESERO) {
            throw ErrorNegocio.prohibido("Solo un gerente o el administrador puede anular pedidos");
        }
        Pedido p = pedido(id);
        if (p.getEstado() == EstadoPedido.ANULADO || p.getEstado() == EstadoPedido.PAGADO) {
            throw ErrorNegocio.conflicto("El pedido ya está " + p.getEstado());
        }
        if (!p.getPagos().isEmpty()) {
            throw ErrorNegocio.conflicto("El pedido tiene pagos registrados; no se puede anular");
        }
        p.setEstado(EstadoPedido.ANULADO);
        p.setMotivoAnulacion(motivo.trim() + " (anuló " + actor.username() + ")");
        p.setCerradoEn(LocalDateTime.now(reloj));
        return PedidoResponse.de(p);
    }

    // ===================== Pagos =====================

    @Transactional
    public PedidoResponse registrarPago(Long id, PagoRequest req, UsuarioActual actor) {
        Pedido p = pedidoEditable(id);
        Turno turno = turnos.turnoAbierto()
                .orElseThrow(() -> ErrorNegocio.conflicto("No hay un turno abierto para registrar el cobro"));

        if (req.metodo() != MetodoPago.EFECTIVO_USD && req.metodo() != MetodoPago.EFECTIVO_BS
                && req.metodo() != MetodoPago.PUNTO_DE_VENTA && limpio(req.referencia()) == null) {
            throw ErrorNegocio.invalido("Indica la referencia del " + req.metodo());
        }

        BigDecimal tasa = p.getTasaBsPorUsd();
        if (tasa == null) {
            tasa = tasas.tasaVigente().orElse(null);
            if (tasa == null && req.metodo().getMoneda() == Moneda.BS) {
                throw ErrorNegocio.invalido("Para cobrar en bolívares primero registra una tasa de cambio");
            }
            if (tasa != null) p.asignarTasa(tasa);   // desde ahora el pedido también vale en Bs
        }

        Pago pago = Pago.de(req.metodo(), req.monto(), tasa, limpio(req.referencia()));
        BigDecimal saldo = p.getSaldoPendienteUsd();
        if (pago.getEquivalenteUsd().compareTo(saldo.add(TOLERANCIA_USD)) > 0) {
            throw ErrorNegocio.invalido("El pago ($" + pago.getEquivalenteUsd() + ") supera el saldo pendiente ($"
                    + saldo + "). Registra solo lo que se cobra; el vuelto no se registra.");
        }
        pago.setTurno(turno);
        pago.setRegistradoPor(usuarios.referencia(actor.id()));
        p.agregarPago(pago);
        cerrarSiCorresponde(p);
        pedidos.flush();
        return PedidoResponse.de(p);
    }

    /** ENTREGADO + saldo ≤ tolerancia ⇒ PAGADO (cerrado). */
    private void cerrarSiCorresponde(Pedido p) {
        if (p.getEstado() == EstadoPedido.ENTREGADO && p.getSaldoPendienteUsd().compareTo(TOLERANCIA_USD) <= 0) {
            p.setEstado(EstadoPedido.PAGADO);
            p.setCerradoEn(LocalDateTime.now(reloj));
        }
    }
}
