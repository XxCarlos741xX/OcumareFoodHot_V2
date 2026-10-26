package com.project.ocumare_food.inventario.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Cuánto de un insumo lleva UN plato (p. ej. Hamburguesa clásica: 1 pan, 0,150 kg de carne). */
@Entity
@Table(name = "recetas", uniqueConstraints = @UniqueConstraint(columnNames = {"producto_id", "insumo_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecetaLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Producto del catálogo (se guarda el id: el inventario no modifica la carta salvo para marcar agotado). */
    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "insumo_id", nullable = false)
    private Insumo insumo;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidad;
}
