package com.project.ocumare_food.service;

import com.project.ocumare_food.dto.CatalogoDto.*;
import com.project.ocumare_food.exception.ErrorNegocio;
import com.project.ocumare_food.model.Categoria;
import com.project.ocumare_food.model.Mesa;
import com.project.ocumare_food.model.Producto;
import com.project.ocumare_food.repository.CategoriaRepository;
import com.project.ocumare_food.repository.MesaRepository;
import com.project.ocumare_food.repository.PedidoRepository;
import com.project.ocumare_food.repository.ProductoRepository;
import com.project.ocumare_food.util.Dinero;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Menú (categorías y productos) y mesas.
 * "Eliminar" es siempre una baja lógica: lo vendido antes sigue apareciendo en los reportes.
 * Los cambios de precio solo afectan pedidos nuevos (cada línea de pedido guarda su precio).
 */
@Service
public class CatalogoService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final MesaRepository mesas;
    private final PedidoRepository pedidos;
    private final OperacionService operacion;

    public CatalogoService(CategoriaRepository categorias, ProductoRepository productos, MesaRepository mesas,
                           PedidoRepository pedidos, OperacionService operacion) {
        this.categorias = categorias;
        this.productos = productos;
        this.mesas = mesas;
        this.pedidos = pedidos;
        this.operacion = operacion;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ===================== Categorías =====================

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listarCategorias() {
        return categorias.findAllByOrderByOrdenAscNombreAsc().stream().map(CategoriaResponse::de).toList();
    }

    @Transactional
    public CategoriaResponse crearCategoria(CategoriaRequest req) {
        String nombre = req.nombre().trim();
        if (categorias.existsByNombreIgnoreCase(nombre)) {
            throw ErrorNegocio.conflicto("Ya existe una categoría con ese nombre");
        }
        Categoria c = Categoria.builder()
                .nombre(nombre)
                .orden(req.orden() == null ? 0 : req.orden())
                .activa(req.activa() == null || req.activa())
                .build();
        return CategoriaResponse.de(categorias.save(c));
    }

    @Transactional
    public CategoriaResponse editarCategoria(Long id, CategoriaRequest req) {
        Categoria c = categoria(id);
        String nombre = req.nombre().trim();
        if (categorias.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw ErrorNegocio.conflicto("Ya existe una categoría con ese nombre");
        }
        c.setNombre(nombre);
        if (req.orden() != null) c.setOrden(req.orden());
        if (req.activa() != null) c.setActiva(req.activa());
        return CategoriaResponse.de(c);
    }

    @Transactional
    public void desactivarCategoria(Long id) {
        categoria(id).setActiva(false);
    }

    private Categoria categoria(Long id) {
        return categorias.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Categoría no encontrada"));
    }

    // ===================== Productos =====================

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarProductos(boolean incluirInactivos) {
        List<Producto> lista = incluirInactivos
                ? productos.findAllByOrderByCategoriaOrdenAscNombreAsc()
                : productos.findByActivoTrueOrderByCategoriaOrdenAscNombreAsc();
        return lista.stream().map(ProductoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtenerProducto(Long id) {
        return ProductoResponse.de(producto(id));
    }

    @Transactional
    public ProductoResponse crearProducto(ProductoRequest req) {
        Producto p = Producto.builder()
                .nombre(req.nombre().trim())
                .descripcion(limpio(req.descripcion()))
                .precioUsd(Dinero.monto(req.precioUsd()))
                .categoria(categoriaActiva(req.categoriaId()))
                .imagenUrl(limpio(req.imagenUrl()))
                .disponible(req.disponible() == null || req.disponible())
                .destacado(req.destacado() != null && req.destacado())
                .build();
        return ProductoResponse.de(productos.save(p));
    }

    @Transactional
    public ProductoResponse editarProducto(Long id, ProductoRequest req) {
        Producto p = producto(id);
        p.setNombre(req.nombre().trim());
        p.setDescripcion(limpio(req.descripcion()));
        p.setPrecioUsd(Dinero.monto(req.precioUsd()));
        if (!p.getCategoria().getId().equals(req.categoriaId())) {
            p.setCategoria(categoriaActiva(req.categoriaId()));
        }
        p.setImagenUrl(limpio(req.imagenUrl()));
        if (req.disponible() != null) p.setDisponible(req.disponible());
        if (req.destacado() != null) p.setDestacado(req.destacado());
        return ProductoResponse.de(p);
    }

    /** Interruptor rápido de "agotado / disponible". */
    @Transactional
    public ProductoResponse cambiarDisponible(Long id, boolean disponible) {
        Producto p = producto(id);
        p.setDisponible(disponible);
        return ProductoResponse.de(p);
    }

    @Transactional
    public void desactivarProducto(Long id) {
        producto(id).setActivo(false);
    }

    @Transactional
    public ProductoResponse reactivarProducto(Long id) {
        Producto p = producto(id);
        p.setActivo(true);
        return ProductoResponse.de(p);
    }

    private Producto producto(Long id) {
        return productos.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Producto no encontrado"));
    }

    private Categoria categoriaActiva(Long id) {
        Categoria c = categoria(id);
        if (!c.isActiva()) throw ErrorNegocio.invalido("La categoría está desactivada");
        return c;
    }

    // ===================== Menú público =====================

    @Transactional(readOnly = true)
    public MenuResponse menuPublico() {
        BigDecimal tasa = operacion.tasaVigente().orElse(null);
        Map<Long, MenuCategoria> porCategoria = new LinkedHashMap<>();
        for (Producto p : productos.findByActivoTrueAndCategoriaActivaTrueOrderByCategoriaOrdenAscNombreAsc()) {
            Categoria c = p.getCategoria();
            porCategoria.computeIfAbsent(c.getId(), k -> new MenuCategoria(c.getId(), c.getNombre(), new ArrayList<>()))
                    .productos()
                    .add(new MenuProducto(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecioUsd(),
                            tasa == null ? null : Dinero.usdABs(p.getPrecioUsd(), tasa),
                            p.getImagenUrl(), p.isDisponible(), p.isDestacado()));
        }
        return new MenuResponse(tasa, List.copyOf(porCategoria.values()));
    }

    // ===================== Mesas =====================

    private Map<Long, Long> mesasOcupadas() {
        Map<Long, Long> ocupadas = new HashMap<>();
        for (Object[] fila : pedidos.mesasOcupadas(PedidoRepository.ESTADOS_ABIERTOS)) {
            ocupadas.put((Long) fila[0], (Long) fila[1]);
        }
        return ocupadas;
    }

    private List<MesaResponse> respuestaMesas(List<Mesa> lista) {
        Map<Long, Long> ocupadas = mesasOcupadas();
        return lista.stream().map(m -> MesaResponse.de(m, ocupadas.get(m.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<MesaResponse> listarMesas(boolean incluirInactivas) {
        return respuestaMesas(incluirInactivas
                ? mesas.findAllByOrderByOrdenAscNumeroAsc()
                : mesas.findByActivaTrueOrderByOrdenAscNumeroAsc());
    }

    private Mesa mesa(Long id) {
        return mesas.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Mesa no encontrada"));
    }

    private boolean ocupada(Long mesaId) {
        return !pedidos.findByMesaIdAndEstadoIn(mesaId, PedidoRepository.ESTADOS_ABIERTOS).isEmpty();
    }

    @Transactional
    public MesaResponse crearMesa(MesaRequest req) {
        Integer numero = req.numero();
        if (numero == null) {
            numero = mesas.findAll().stream().mapToInt(Mesa::getNumero).max().orElse(0) + 1;
        } else if (mesas.existsByNumero(numero)) {
            throw ErrorNegocio.conflicto("Ya existe la mesa " + numero);
        }
        Mesa m = Mesa.builder()
                .numero(numero)
                .nombre(limpio(req.nombre()))
                .capacidad(req.capacidad() == null ? 4 : req.capacidad())
                .zona(limpio(req.zona()))
                .orden(req.orden() == null ? mesas.maxOrden() + 1 : req.orden())
                .activa(req.activa() == null || req.activa())
                .build();
        return MesaResponse.de(mesas.save(m), null);
    }

    @Transactional
    public MesaResponse editarMesa(Long id, MesaRequest req) {
        Mesa m = mesa(id);
        boolean estaOcupada = ocupada(id);
        if (req.numero() != null && !req.numero().equals(m.getNumero())) {
            if (estaOcupada) throw ErrorNegocio.conflicto("La mesa " + m.getNumero() + " está ocupada; cambia el número cuando se libere");
            if (mesas.existsByNumeroAndIdNot(req.numero(), id)) throw ErrorNegocio.conflicto("Ya existe la mesa " + req.numero());
            m.setNumero(req.numero());
        }
        if (req.activa() != null && !req.activa() && m.isActiva() && estaOcupada) {
            throw ErrorNegocio.conflicto("La mesa tiene un pedido abierto; ciérralo antes de desactivarla");
        }
        m.setNombre(limpio(req.nombre()));
        m.setZona(limpio(req.zona()));
        if (req.capacidad() != null) m.setCapacidad(req.capacidad());
        if (req.orden() != null) m.setOrden(req.orden());
        if (req.activa() != null) m.setActiva(req.activa());
        return MesaResponse.de(m, mesasOcupadas().get(id));
    }

    @Transactional
    public void desactivarMesa(Long id) {
        Mesa m = mesa(id);
        if (ocupada(id)) {
            throw ErrorNegocio.conflicto("La mesa tiene un pedido abierto; ciérralo antes de desactivarla");
        }
        m.setActiva(false);
    }

    /** Guarda el orden de las mesas (las que no vengan en la lista conservan su orden, después). */
    @Transactional
    public List<MesaResponse> ordenarMesas(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw ErrorNegocio.invalido("Hay mesas repetidas en la lista");
        Map<Long, Mesa> porId = new HashMap<>();
        mesas.findAllById(ids).forEach(m -> porId.put(m.getId(), m));
        if (porId.size() != ids.size()) throw ErrorNegocio.noEncontrado("Alguna de las mesas no existe");
        for (int i = 0; i < ids.size(); i++) porId.get(ids.get(i)).setOrden(i + 1);
        int siguiente = ids.size() + 1;
        for (Mesa m : mesas.findAllByOrderByOrdenAscNumeroAsc()) {
            if (!porId.containsKey(m.getId())) m.setOrden(siguiente++);
        }
        return listarMesas(true);
    }

    /**
     * Renumera 1, 2, 3… las mesas activas según su orden. Las inactivas quedan después,
     * para no chocar. No se permite con mesas ocupadas (el mesero vería cambiar los números).
     */
    @Transactional
    public List<MesaResponse> renumerarMesas() {
        if (!mesasOcupadas().isEmpty()) {
            throw ErrorNegocio.conflicto("Hay mesas ocupadas; renumera cuando todas estén libres");
        }
        List<Mesa> todas = new ArrayList<>(mesas.findAllByOrderByOrdenAscNumeroAsc());
        todas.sort(Comparator.comparing((Mesa m) -> !m.isActiva()));   // activas primero, sin perder el orden
        // Paso 1: números temporales negativos para no violar la regla de número único al intercambiar.
        todas.forEach(m -> m.setNumero(-m.getId().intValue()));
        mesas.flush();
        for (int i = 0; i < todas.size(); i++) {
            todas.get(i).setNumero(i + 1);
            todas.get(i).setOrden(i + 1);
        }
        mesas.flush();
        return listarMesas(true);
    }
}
