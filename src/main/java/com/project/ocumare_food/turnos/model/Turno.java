package com.project.ocumare_food.turnos.model;

import com.project.ocumare_food.usuarios.model.Usuario;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Turno de trabajo (día o noche). Todo pedido pertenece al turno abierto al crearse.
 * Al cerrar se congelan los totales: el corte no cambia aunque luego se edite algo.
 */
@Entity
@Table(name = "turnos", uniqueConstraints =
        @UniqueConstraint(name = "uk_turno_fecha_tipo", columnNames = {"fecha_operativa", "tipo"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Turno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTurno tipo;

    /**
     * Día contable del turno. El turno de noche que pasa de medianoche
     * sigue perteneciendo al día en que empezó.
     */
    @Column(name = "fecha_operativa", nullable = false)
    private LocalDate fechaOperativa;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoTurno estado = EstadoTurno.ABIERTO;

    @Column(name = "abierto_en", nullable = false)
    private LocalDateTime abiertoEn;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "abierto_por_id", nullable = false)
    private Usuario abiertoPor;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cerrado_por_id")
    private Usuario cerradoPor;

    // ---- Fondo de caja con que arranca el turno (efectivo para dar vuelto) ----

    @Builder.Default
    @Column(name = "fondo_inicial_usd", nullable = false, precision = 14, scale = 2)
    private BigDecimal fondoInicialUsd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "fondo_inicial_bs", nullable = false, precision = 18, scale = 2)
    private BigDecimal fondoInicialBs = BigDecimal.ZERO;

    // ---- Totales congelados al cerrar ----

    @Column(name = "cantidad_pedidos")
    private Integer cantidadPedidos;

    /** Venta total del turno expresada en USD. */
    @Column(name = "total_venta_usd", precision = 14, scale = 2)
    private BigDecimal totalVentaUsd;

    /** Lo que entró en dólares (efectivo $ + Zelle). */
    @Column(name = "recaudado_usd", precision = 14, scale = 2)
    private BigDecimal recaudadoUsd;

    /** Lo que entró en bolívares (efectivo Bs + pago móvil + punto + transferencia). */
    @Column(name = "recaudado_bs", precision = 18, scale = 2)
    private BigDecimal recaudadoBs;

    /** Efectivo que el sistema espera en caja al cierre = fondo inicial + cobros en efectivo. */
    @Column(name = "efectivo_usd_esperado", precision = 14, scale = 2)
    private BigDecimal efectivoUsdEsperado;

    @Column(name = "efectivo_bs_esperado", precision = 18, scale = 2)
    private BigDecimal efectivoBsEsperado;

    // ---- Conteo de caja (opcional; se puede registrar al cerrar o después) ----

    @Column(name = "efectivo_usd_contado", precision = 14, scale = 2)
    private BigDecimal efectivoUsdContado;

    @Column(name = "efectivo_bs_contado", precision = 18, scale = 2)
    private BigDecimal efectivoBsContado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conteo_por_id")
    private Usuario conteoPor;

    @Column(name = "conteo_en")
    private LocalDateTime conteoEn;

    @Column(length = 500)
    private String observaciones;
}
