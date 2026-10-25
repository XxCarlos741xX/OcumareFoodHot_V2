package com.project.ocumare_food.cocina.model;

import com.project.ocumare_food.pedidos.model.TipoPedido;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Hoja que sale en la impresora de cocina. Guarda una copia de lo que había que preparar en ese
 * momento (no se recalcula si el pedido cambia después) y si ya se imprimió.
 */
@Entity
@Table(name = "comandas", indexes = {
        @Index(name = "ix_comanda_pedido", columnList = "pedido_id"),
        @Index(name = "ix_comanda_pendiente", columnList = "impresa_en, fallida_en, creada_en")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comanda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Vacío en la hoja de prueba. */
    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "numero_pedido")
    private Integer numeroPedido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoComandaCocina tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pedido", length = 15)
    private TipoPedido tipoPedido;

    @Column(name = "mesa_numero")
    private Integer mesaNumero;

    @Column(name = "cliente_nombre", length = 100)
    private String clienteNombre;

    /** DELIVERY o RETIRO (web y delivery). */
    @Column(length = 10)
    private String entrega;

    @Column(name = "atendido_por", length = 100)
    private String atendidoPor;

    @Column(name = "solicitado_por", length = 100)
    private String solicitadoPor;

    @Column(name = "notas_pedido", length = 500)
    private String notasPedido;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "comanda_lineas", joinColumns = @JoinColumn(name = "comanda_id"))
    @OrderColumn(name = "orden")
    private List<LineaComanda> lineas = new ArrayList<>();

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    /** Cuándo la impresora confirmó que la imprimió. */
    @Column(name = "impresa_en")
    private LocalDateTime impresaEn;

    @Column(name = "impresora_id")
    private Long impresoraId;

    /** Intentos que la impresora reportó como fallidos (sin papel, tapa abierta…). */
    @Builder.Default
    @Column(nullable = false)
    private int fallos = 0;

    /** Se dejó de intentar después de varios fallos seguidos. Se puede reimprimir desde el pedido. */
    @Column(name = "fallida_en")
    private LocalDateTime fallidaEn;
}
