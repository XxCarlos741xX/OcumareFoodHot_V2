package com.project.ocumare_food.inventario.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Producto que el inventario marcó agotado porque se acabó un insumo de su receta.
 * Cuando vuelve a haber insumos (compra, conteo o devolución), el inventario lo vuelve a poner disponible.
 * Si el gerente lo agota a mano, no queda aquí y el inventario no lo toca.
 */
@Entity
@Table(name = "agotados_por_inventario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgotadoPorInventario {

    @Id
    @Column(name = "producto_id")
    private Long productoId;

    @Column(name = "insumo", length = 80)
    private String insumo;

    @Column(name = "desde", nullable = false)
    private LocalDateTime desde;
}
