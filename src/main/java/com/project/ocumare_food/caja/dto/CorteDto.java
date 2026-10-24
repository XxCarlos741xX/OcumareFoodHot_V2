package com.project.ocumare_food.caja.dto;

import com.project.ocumare_food.turnos.dto.OperacionDto;

import com.project.ocumare_food.turnos.dto.OperacionDto.TurnoResponse;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.moneda.model.Moneda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cortes de turno y dashboard.
 *
 * La respuesta del corte está pensada para la pantalla: primero "totales" (lo que se ve de entrada)
 * y aparte "conteoCaja" (lo que va en el desplegable).
 */
public final class CorteDto {

    private CorteDto() {
    }

    // ---------- Peticiones ----------

    public record ConteoRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal efectivoUsd,
            @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal efectivoBs) {
    }

    /** El conteo es opcional al cerrar: puede registrarse después. */
    public record CerrarTurnoRequest(@Valid ConteoRequest conteo, @Size(max = 500) String observaciones) {
    }

    // ---------- Piezas del corte ----------

    /** Lo que se ve de entrada. */
    public record Totales(
            long pedidos,               // pedidos válidos (sin anulados)
            long anulados,
            long pendientes,            // siguen abiertos (no pagados/cerrados)
            BigDecimal ventaUsd,        // valor de los pedidos válidos
            BigDecimal recaudadoUsd,    // lo que entró en dólares (efectivo $ + Zelle)
            BigDecimal recaudadoBs,     // lo que entró en bolívares (efectivo Bs + pago móvil + punto + transferencia)
            BigDecimal recaudadoTotalEquivUsd,   // todo lo cobrado expresado en USD
            BigDecimal ticketPromedioUsd) {
    }

    public record LineaMetodo(MetodoPago metodo, Moneda moneda, long cantidad, BigDecimal monto, BigDecimal equivalenteUsd) {
    }

    public record LineaMesero(Long meseroId, String mesero, long pedidos, BigDecimal ventaUsd,
                              BigDecimal cobradoUsd, BigDecimal cobradoBs, BigDecimal cobradoEquivUsd) {
    }

    /** Resultado de comparar contado vs esperado en una moneda. */
    public enum Cuadre { CUADRA, SOBRA, FALTA, SIN_CONTAR }

    /** Lo que va en el desplegable. Solo efectivo: pago móvil, punto, Zelle y transferencias van al banco. */
    public record ConteoCaja(
            BigDecimal fondoInicialUsd, BigDecimal fondoInicialBs,
            BigDecimal cobradoEfectivoUsd, BigDecimal cobradoEfectivoBs,
            BigDecimal esperadoUsd, BigDecimal esperadoBs,
            BigDecimal contadoUsd, BigDecimal contadoBs,
            BigDecimal diferenciaUsd, BigDecimal diferenciaBs,
            Cuadre cuadreUsd, Cuadre cuadreBs,
            String contadoPor, LocalDateTime contadoEn) {
    }

    public record CorteTurno(TurnoResponse turno, String observaciones, Totales totales,
                             List<LineaMetodo> porMetodo, List<LineaMesero> porMesero, ConteoCaja conteoCaja) {
    }

    // ---------- Dashboard ----------

    public record TurnoDelDia(Long turnoId, String tipo, String estado, Totales totales) {
    }

    public record DashboardDia(LocalDate fecha, Totales totales, List<TurnoDelDia> turnos,
                               List<LineaMetodo> porMetodo, List<LineaMesero> porMesero) {
    }

    public record PuntoSerie(LocalDate fecha, BigDecimal recaudadoUsd, BigDecimal recaudadoBs, BigDecimal totalEquivUsd) {
    }

    public record DashboardRango(LocalDate desde, LocalDate hasta, BigDecimal recaudadoUsd, BigDecimal recaudadoBs,
                                 BigDecimal totalEquivUsd, List<PuntoSerie> dias) {
    }
}
