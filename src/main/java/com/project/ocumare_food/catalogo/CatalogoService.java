package com.project.ocumare_food.catalogo;


import com.project.ocumare_food.catalogo.dto.CatalogoDto.*;
import com.project.ocumare_food.moneda.TasaService;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.catalogo.model.Categoria;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.catalogo.repository.CategoriaRepository;
import com.project.ocumare_food.catalogo.repository.ProductoRepository;
import com.project.ocumare_food.compartido.Dinero;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Menú: categorías y productos (API pública del módulo catalogo).
 * Pedidos usa {@link #productosPorId(Collection)}; la web usa {@link #menuPublico()}.
 * "Eliminar" es siempre una baja lógica: lo vendido antes sigue apareciendo en los reportes.
 * Los cambios de precio solo afectan pedidos nuevos (cada línea de pedido guarda su precio).
 */
@Service
public class CatalogoService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final TasaService tasas;

    public CatalogoService(CategoriaRepository categorias, ProductoRepository productos, TasaService tasas) {
        this.categorias = categorias;
        this.productos = productos;
        this.tasas = tasas;
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

    /** Para otros módulos (pedidos): los productos con esos ids (los que no existan no vienen). */
    @Transactional(readOnly = true)
    public List<Producto> productosPorId(Collection<Long> ids) {
        return productos.findAllById(ids);
    }

    // ===================== Menú público =====================

    @Transactional(readOnly = true)
    public MenuResponse menuPublico() {
        BigDecimal tasa = tasas.tasaVigente().orElse(null);
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
}
