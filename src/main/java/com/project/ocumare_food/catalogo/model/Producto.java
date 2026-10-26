package com.project.ocumare_food.catalogo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Producto del menú. El precio base está en dólares; el precio en bolívares
 * se calcula con la tasa vigente al momento de vender.
 * Cambiar el precio aquí NO altera pedidos viejos: cada detalle guarda su propio precio.
 */
@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "precio_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUsd;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    /** Agotado por hoy (se apaga y se prende rápido desde el panel). */
    @Builder.Default
    @Column(nullable = false)
    private boolean disponible = true;

    /**
     * Impulsado por el gerente: sale primero en el carrusel "Lo más pedido" de la web, venda o no.
     * El resto del carrusel se llena solo con los más vendidos (ver CatalogoService.menuPublico).
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean destacado = false;

    /** Baja lógica: el producto sale del menú pero sigue en el historial de ventas. */
    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private LocalDateTime actualizadoEn;
}
