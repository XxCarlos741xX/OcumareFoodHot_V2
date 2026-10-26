package com.project.ocumare_food.inventario.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cada entrada o salida de un insumo. Nunca se edita ni se borra: es el historial (kárdex). */
@Entity
@Table(name = "movimientos_inventario", indexes = {
        @Index(name = "idx_mov_insumo", columnList = "insumo_id, id"),
        @Index(name = "idx_mov_pedido", columnList = "pedido_id")})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoMovimiento tipo;

    /** Positiva si suma al stock, negativa si resta. */
    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;

    @Column(name = "stock_resultante", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockResultante;

    @Column(name = "costo_unitario_usd", precision = 12, scale = 4)
    private BigDecimal costoUnitarioUsd;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "numero_pedido")
    private Integer numeroPedido;

    @Column(length = 200)
    private String nota;

    /** Quien lo registró (nombre); en los automáticos, quien envió o canceló el pedido. */
    @Column(length = 100)
    private String usuario;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;
}
