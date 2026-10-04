package com.project.ocumare_food.dto;

import com.project.ocumare_food.model.Categoria;
import com.project.ocumare_food.model.Mesa;
import com.project.ocumare_food.model.Producto;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

/** Peticiones y respuestas del menú (categorías y productos) y de las mesas. */
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

    public record MenuProducto(Long id, String nombre, String descripcion, BigDecimal precioUsd,
                               BigDecimal precioBs, String imagenUrl, boolean disponible, boolean destacado) {
    }

    public record MenuCategoria(Long id, String nombre, List<MenuProducto> productos) {
    }

    /** tasaBsPorUsd puede venir vacía si nunca se ha registrado una tasa. */
    public record MenuResponse(BigDecimal tasaBsPorUsd, List<MenuCategoria> categorias) {
    }

    // ---------- Mesas ----------

    /** numero es opcional al crear: si no viene, se asigna el siguiente libre. */
    public record MesaRequest(
            @Min(1) @Max(9999) Integer numero,
            @Size(max = 40) String nombre,
            @Min(1) @Max(50) Integer capacidad,
            @Size(max = 40) String zona,
            @Min(0) @Max(9999) Integer orden,
            Boolean activa) {
    }

    /** Lista de ids de mesas en el orden en que deben quedar (p. ej. tras arrastrar y soltar). */
    public record OrdenMesasRequest(@NotEmpty @Size(max = 500) List<@NotNull Long> ids) {
    }

    /** pedidoAbiertoId != null significa que la mesa está ocupada. */
    public record MesaResponse(Long id, Integer numero, String nombre, int capacidad, String zona, int orden,
                               boolean activa, Long pedidoAbiertoId) {
        public static MesaResponse de(Mesa m, Long pedidoAbiertoId) {
            return new MesaResponse(m.getId(), m.getNumero(), m.getNombre(), m.getCapacidad(), m.getZona(),
                    m.getOrden(), m.isActiva(), pedidoAbiertoId);
        }
    }
}
