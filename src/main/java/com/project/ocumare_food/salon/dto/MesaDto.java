package com.project.ocumare_food.salon.dto;

import com.project.ocumare_food.salon.model.Mesa;
import jakarta.validation.constraints.*;

import java.util.List;

/** Peticiones y respuestas de las mesas. */
public final class MesaDto {

    private MesaDto() {
    }

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
