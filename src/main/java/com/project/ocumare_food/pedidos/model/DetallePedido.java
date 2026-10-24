package com.project.ocumare_food.pedidos.model;

import com.project.ocumare_food.catalogo.model.Producto;

import com.project.ocumare_food.compartido.Dinero;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Línea de un pedido. Copia el nombre y el precio del producto al momento de pedir:
 * si mañana cambia el precio en el menú, este pedido conserva el precio con que se vendió.
 */
@Entity
@Table(name = "detalles_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DetallePedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "nombre_producto", nullable = false, length = 120)
    private String nombreProducto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitarioUsd;

    @Column(name = "subtotal_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalUsd;

    /** "Sin cebolla", "bien cocida"… */
    @Column(length = 200)
    private String notas;

    /** Crea la línea copiando nombre y precio actuales del producto. */
    public static DetallePedido de(Producto producto, int cantidad, String notas) {
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        BigDecimal precio = Dinero.monto(producto.getPrecioUsd());
        return DetallePedido.builder()
                .producto(producto)
                .nombreProducto(producto.getNombre())
                .cantidad(cantidad)
                .precioUnitarioUsd(precio)
                .subtotalUsd(Dinero.monto(precio.multiply(BigDecimal.valueOf(cantidad))))
                .notas(notas)
                .build();
    }
}
