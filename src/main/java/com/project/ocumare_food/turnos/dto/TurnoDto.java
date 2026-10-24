package com.project.ocumare_food.turnos.dto;

import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.turnos.model.EstadoTurno;
import com.project.ocumare_food.turnos.model.TipoTurno;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Turnos. */
public final class TurnoDto {

    private TurnoDto() {
    }

    /** fechaOperativa es opcional: si no viene, se usa la fecha de hoy (hora de Venezuela). */
    public record AbrirTurnoRequest(
            @NotNull TipoTurno tipo,
            LocalDate fechaOperativa,
            /** Efectivo con que arranca la caja (para dar vuelto). Opcional, por defecto 0. */
            @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal fondoInicialUsd,
            @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal fondoInicialBs) {
    }

    public record TurnoResponse(Long id, TipoTurno tipo, LocalDate fechaOperativa, EstadoTurno estado,
                                LocalDateTime abiertoEn, String abiertoPor,
                                LocalDateTime cerradoEn, String cerradoPor,
                                Integer cantidadPedidos, BigDecimal recaudadoUsd, BigDecimal recaudadoBs) {
        public static TurnoResponse de(Turno t) {
            return new TurnoResponse(t.getId(), t.getTipo(), t.getFechaOperativa(), t.getEstado(),
                    t.getAbiertoEn(), t.getAbiertoPor().getNombre(),
                    t.getCerradoEn(), t.getCerradoPor() == null ? null : t.getCerradoPor().getNombre(),
                    t.getCantidadPedidos(), t.getRecaudadoUsd(), t.getRecaudadoBs());
        }
    }
}
