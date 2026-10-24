package com.project.ocumare_food.moneda.dto;

import com.project.ocumare_food.moneda.model.TasaCambio;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Tasa de cambio. */
public final class TasaDto {

    private TasaDto() {
    }

    public record TasaRequest(
            @NotNull @DecimalMin(value = "0.0001", message = "debe ser mayor que 0")
            @Digits(integer = 10, fraction = 4) BigDecimal bsPorUsd,
            @Size(max = 30) String fuente) {
    }

    public record TasaResponse(Long id, BigDecimal bsPorUsd, LocalDateTime vigenteDesde, String fuente,
                               String registradaPor) {
        public static TasaResponse de(TasaCambio t) {
            return new TasaResponse(t.getId(), t.getBsPorUsd(), t.getVigenteDesde(), t.getFuente(),
                    t.getRegistradaPor() == null ? null : t.getRegistradaPor().getNombre());
        }
    }
}
