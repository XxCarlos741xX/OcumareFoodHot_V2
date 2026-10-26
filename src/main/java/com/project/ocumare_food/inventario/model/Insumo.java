package com.project.ocumare_food.inventario.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Lo que se compra y se gasta en la cocina: carne, pan, queso, refresco…
 * El stock puede quedar negativo (la venta nunca se frena por el inventario); eso se muestra como alerta
 * para que el personal registre la compra o corrija con un conteo.
 *
 * El stock solo cambia con las consultas de InsumoRepository (suma atómica). DynamicUpdate hace que editar
 * el nombre o el mínimo no reescriba el stock con un valor viejo si justo entró un pedido.
 */
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(name = "insumos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Insumo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unidad unidad;

    @Builder.Default
    @Column(name = "stock_actual", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockActual = BigDecimal.ZERO;

    /** Por debajo de esto sale la alerta de "stock bajo". */
    @Builder.Default
    @Column(name = "stock_minimo", nullable = false, precision = 14, scale = 3)
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    /** Costo de una unidad (el de la última compra), para calcular cuánto cuesta cada plato. */
    @Column(name = "costo_unitario_usd", precision = 12, scale = 4)
    private BigDecimal costoUnitarioUsd;

    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
