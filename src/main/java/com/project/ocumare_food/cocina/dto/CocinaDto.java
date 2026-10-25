package com.project.ocumare_food.cocina.dto;

import com.project.ocumare_food.cocina.model.TipoComandaCocina;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Comandas de cocina e impresoras. */
public final class CocinaDto {

    private CocinaDto() {
    }

    /** estado: PENDIENTE, IMPRESA, FALLIDA (se dejó de intentar) o VENCIDA (muy vieja, no se imprimió). */
    public record ComandaResponse(Long id, Long pedidoId, Integer numeroPedido, TipoComandaCocina tipo, String estado,
                                  LocalDateTime creadaEn, LocalDateTime impresaEn, int fallos) {
    }

    /**
     * Los minutos se calculan en el servidor: así el aviso sale bien aunque la tablet tenga otra zona horaria.
     * minutosSinConexion es null si la impresora nunca se ha conectado.
     */
    public record EstadoCocina(int impresorasActivas, LocalDateTime ultimaConexion, boolean conectada,
                               Long minutosSinConexion, long pendientes, LocalDateTime pendienteMasVieja,
                               Long minutosPendienteMasVieja) {
    }

    public record CrearImpresoraRequest(@NotBlank @Size(max = 60) String nombre,
                                        @Min(24) @Max(64) Integer columnas) {
    }

    public record ImpresoraResponse(Long id, String nombre, int columnas, boolean activa, LocalDateTime creadaEn,
                                    LocalDateTime ultimaConexion, String ultimaVia, String ultimoEstado,
                                    boolean conectada) {
    }

    /** Las rutas son relativas: el POS les antepone la dirección del sitio (https://ocumarefoodhot.com). */
    public record ImpresoraCreada(ImpresoraResponse impresora, String token, String rutaCloudPrnt, String rutaPuente) {
    }
}
