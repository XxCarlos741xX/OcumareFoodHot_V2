package com.project.ocumare_food.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historial de tasas (Bs por cada USD). La vigente es la más reciente.
 * Se guarda como historial, no como un valor que se sobreescribe, para poder auditar.
 */
@Entity
@Table(name = "tasas_cambio", indexes = @Index(name = "ix_tasa_vigente_desde", columnList = "vigente_desde"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TasaCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bs_por_usd", nullable = false, precision = 14, scale = 4)
    private BigDecimal bsPorUsd;

    @Column(name = "vigente_desde", nullable = false)
    private LocalDateTime vigenteDesde;

    /** Ej.: "BCV", "Paralelo", "Manual". */
    @Column(length = 30)
    private String fuente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por_id")
    private Usuario registradaPor;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
