package com.project.ocumare_food.catalogo.dto;

import com.project.ocumare_food.catalogo.model.Categoria;
import com.project.ocumare_food.catalogo.model.Producto;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Peticiones y respuestas del menú (categorías y productos). */
public final class CatalogoDto {

    private CatalogoDto() {
    }

    // ---------- Categorías ----------

    public record CategoriaRequest(
            @NotBlank @Size(max = 60) String nombre,
            @Min(0) @Max(999) Integer orden,
            Boolean activa) {
    }

    public record CategoriaResponse(Long id, String nombre, int orden, boolean activa) {
        public static CategoriaResponse de(Categoria c) {
            return new CategoriaResponse(c.getId(), c.getNombre(), c.getOrden(), c.isActiva());
        }
    }

    // ---------- Productos ----------

    public record ProductoRequest(
            @NotBlank @Size(max = 120) String nombre,
            @Size(max = 500) String descripcion,
            @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
            @Digits(integer = 8, fraction = 2) BigDecimal precioUsd,
            @NotNull Long categoriaId,
            @Size(max = 500) String imagenUrl,
            Boolean disponible,
            Boolean destacado) {
    }

    public record DisponibleRequest(@NotNull Boolean disponible) {
    }

    public record ProductoResponse(Long id, String nombre, String descripcion, BigDecimal precioUsd,
                                   Long categoriaId, String categoria, String imagenUrl,
                                   boolean disponible, boolean destacado, boolean activo) {
        public static ProductoResponse de(Producto p) {
            return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecioUsd(),
                    p.getCategoria().getId(), p.getCategoria().getNombre(), p.getImagenUrl(),
                    p.isDisponible(), p.isDestacado(), p.isActivo());
        }
    }

    // ---------- Menú público (web) ----------

    /**
     * destacado: sale en el carrusel "Lo más pedido". motivoCarrusel dice por qué:
     * IMPULSADO (lo fijó el gerente) o MAS_VENDIDO (de los más vendidos de los últimos días); null si no sale.
     */
    public record MenuProducto(Long id, String nombre, String descripcion, BigDecimal precioUsd,
                               BigDecimal precioBs, String imagenUrl, boolean disponible, boolean destacado,
                               String motivoCarrusel) {
    }

    public record MenuCategoria(Long id, String nombre, List<MenuProducto> productos) {
    }

    /** Producto del carrusel, en el orden en que se muestra (primero los impulsados). */
    public record CarruselItem(Long id, String motivo) {
    }

    /** tasaBsPorUsd puede venir vacía si nunca se ha registrado una tasa. */
    public record MenuResponse(BigDecimal tasaBsPorUsd, List<MenuCategoria> categorias, List<CarruselItem> carrusel) {
    }
}
