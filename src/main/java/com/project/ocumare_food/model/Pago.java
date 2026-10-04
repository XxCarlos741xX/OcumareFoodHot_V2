package com.project.ocumare_food.model;

import com.project.ocumare_food.model.enums.MetodoPago;
import com.project.ocumare_food.model.enums.Moneda;
import com.project.ocumare_food.util.Dinero;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un pago de un pedido. Un pedido puede tener varios (pago mixto):
 * por ejemplo $5 en efectivo + el resto en Bs por pago móvil.
 * El corte de caja suma por moneda real; el equivalente en USD sirve para saber si ya se pagó todo.
 */
@Entity
@Table(name = "pagos", indexes = {
        @Index(name = "ix_pago_pedido", columnList = "pedido_id"),
        @Index(name = "ix_pago_turno", columnList = "turno_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Moneda moneda;

    /** Monto en la moneda en que realmente se pagó. */
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal monto;

    /** Tasa usada para convertir. Puede ir vacía en pagos en USD si no hay ninguna tasa registrada. */
    @Column(name = "tasa_bs_por_usd", precision = 14, scale = 4)
    private BigDecimal tasaBsPorUsd;

    /**
     * Turno en que entró el dinero. Puede ser distinto al turno del pedido
     * (una mesa abierta de día que se paga de noche): el corte de caja suma por este campo.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "equivalente_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal equivalenteUsd;

    /** Referencia del pago móvil / transferencia / Zelle. */
    @Column(length = 60)
    private String referencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id")
    private Usuario registradoPor;

    @CreationTimestamp
    @Column(name = "registrado_en", nullable = false, updatable = false)
    private LocalDateTime registradoEn;

    /** Crea el pago calculando moneda y equivalente en USD a partir del método. */
    public static Pago de(MetodoPago metodo, BigDecimal monto, BigDecimal tasaBsPorUsd, String referencia) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser mayor que cero");
        }
        BigDecimal montoRedondeado = Dinero.monto(monto);
        BigDecimal equivalente = metodo.getMoneda() == Moneda.USD
                ? montoRedondeado
                : Dinero.bsAUsd(montoRedondeado, tasaBsPorUsd);
        return Pago.builder()
                .metodo(metodo)
                .moneda(metodo.getMoneda())
                .monto(montoRedondeado)
                .tasaBsPorUsd(tasaBsPorUsd)
                .equivalenteUsd(equivalente)
                .referencia(referencia)
                .build();
    }
}
