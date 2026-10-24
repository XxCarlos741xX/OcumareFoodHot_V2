package com.project.ocumare_food.pedidos;

import com.project.ocumare_food.moneda.model.Moneda;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import com.project.ocumare_food.pedidos.model.MetodoPago;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Filas de resumen de ventas y recaudo que el módulo pedidos entrega a caja (cortes y dashboard).
 * Son parte de la API pública de pedidos: caja no consulta pedidos ni pagos directamente.
 */
public final class ResumenVentas {

    private ResumenVentas() {
    }

    /** Pedidos agrupados por estado. */
    public record PorEstado(EstadoPedido estado, Long cantidad, BigDecimal totalUsd) {
    }

    /** Venta por mesero (meseroId null = pedidos web). */
    public record VentaPorMesero(Long meseroId, String mesero, Long pedidos, BigDecimal ventaUsd) {
    }

    /** Recaudo por método de pago. */
    public record PorMetodo(MetodoPago metodo, Moneda moneda, Long cantidad, BigDecimal monto, BigDecimal equivalenteUsd) {
    }

    /** Recaudo por mesero dueño del pedido y moneda. */
    public record PorMeseroMoneda(Long meseroId, String mesero, Moneda moneda, BigDecimal monto, BigDecimal equivalenteUsd) {
    }

    /** Recaudo por día operativo y moneda. */
    public record PorDiaMoneda(LocalDate fecha, Moneda moneda, BigDecimal monto, BigDecimal equivalenteUsd) {
    }
}
