package com.project.ocumare_food.inventario.dto;

import com.project.ocumare_food.inventario.model.Insumo;
import com.project.ocumare_food.inventario.model.MovimientoInventario;
import com.project.ocumare_food.inventario.model.TipoMovimiento;
import com.project.ocumare_food.inventario.model.Unidad;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Insumos, recetas y movimientos del inventario. */
public final class InventarioDto {

    private InventarioDto() {
    }

    // ---------- Insumos ----------

    public record InsumoRequest(
            @NotBlank @Size(max = 80) String nombre,
            @NotNull Unidad unidad,
            @DecimalMin("0") @Digits(integer = 10, fraction = 3) BigDecimal stockMinimo,
            @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal costoUnitarioUsd,
            /** Solo al crear: lo que hay hoy (queda como primer AJUSTE en el historial). */
            @DecimalMin("0") @Digits(integer = 10, fraction = 3) BigDecimal stockInicial,
            Boolean activo) {
    }

    /** bajo: stock en o por debajo del mínimo. usadoEn: cuántos platos lo llevan en la receta. */
    public record InsumoResponse(Long id, String nombre, Unidad unidad, BigDecimal stockActual, BigDecimal stockMinimo,
                                 BigDecimal costoUnitarioUsd, boolean activo, boolean bajo, int usadoEn,
                                 LocalDateTime actualizadoEn) {
        public static InsumoResponse de(Insumo i, int usadoEn) {
            return new InsumoResponse(i.getId(), i.getNombre(), i.getUnidad(), i.getStockActual(), i.getStockMinimo(),
                    i.getCostoUnitarioUsd(), i.isActivo(),
                    i.isActivo() && i.getStockActual().compareTo(i.getStockMinimo()) <= 0, usadoEn, i.getActualizadoEn());
        }
    }

    // ---------- Recetas ----------

    public record LineaRecetaRequest(@NotNull Long insumoId,
                                     @NotNull @DecimalMin(value = "0.001", message = "debe ser mayor que 0")
                                     @Digits(integer = 10, fraction = 3) BigDecimal cantidad) {
    }

    public record RecetaRequest(@NotNull @Size(max = 30) List<@Valid LineaRecetaRequest> lineas) {
    }

    public record LineaRecetaResponse(Long insumoId, String insumo, Unidad unidad, BigDecimal cantidad,
                                      BigDecimal costoUsd, BigDecimal stockActual) {
    }

    /**
     * costoUsd: lo que cuestan los insumos de un plato (null si a algún insumo le falta el costo).
     * alcanzaPara: cuántos platos se pueden hacer con el stock de hoy (null si no tiene receta).
     */
    public record RecetaResponse(Long productoId, String producto, BigDecimal precioUsd, List<LineaRecetaResponse> lineas,
                                 BigDecimal costoUsd, Integer alcanzaPara, boolean agotadoPorInventario) {
    }

    // ---------- Movimientos ----------

    /**
     * ENTRADA y MERMA: cantidad que entró o se perdió (siempre positiva).
     * AJUSTE: cantidad = lo que se contó físicamente (el sistema calcula la diferencia).
     */
    public record MovimientoRequest(
            @NotNull Long insumoId,
            @NotNull TipoMovimiento tipo,
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 3) BigDecimal cantidad,
            @DecimalMin("0") @Digits(integer = 8, fraction = 4) BigDecimal costoUnitarioUsd,
            @Size(max = 200) String nota) {
    }

    public record MovimientoResponse(Long id, Long insumoId, String insumo, Unidad unidad, TipoMovimiento tipo,
                                     BigDecimal cantidad, BigDecimal stockResultante, BigDecimal costoUnitarioUsd,
                                     Long pedidoId, Integer numeroPedido, String nota, String usuario, LocalDateTime creadoEn) {
        public static MovimientoResponse de(MovimientoInventario m) {
            Insumo i = m.getInsumo();
            return new MovimientoResponse(m.getId(), i.getId(), i.getNombre(), i.getUnidad(), m.getTipo(), m.getCantidad(),
                    m.getStockResultante(), m.getCostoUnitarioUsd(), m.getPedidoId(), m.getNumeroPedido(), m.getNota(),
                    m.getUsuario(), m.getCreadoEn());
        }
    }

    /** Para el aviso del POS: insumos en o bajo el mínimo y platos que el inventario dejó agotados. */
    public record AlertasInventario(List<InsumoResponse> bajoMinimo, List<String> platosAgotados) {
    }
}
