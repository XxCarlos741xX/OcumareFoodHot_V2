package com.project.ocumare_food.pedidos.model;

import com.project.ocumare_food.usuarios.model.Usuario;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Una línea de la bitácora de un pedido: quién hizo qué y cuándo.
 * Nunca se edita ni se borra. "alerta" marca lo que el cajero o el gerente deben revisar:
 * platos quitados después de enviarlos a cocina, anulaciones y reimpresiones.
 */
@Entity
@Table(name = "eventos_pedido",
        indexes = {
                @Index(name = "ix_evento_pedido", columnList = "pedido_id"),
                @Index(name = "ix_evento_turno", columnList = "turno_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventoPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    /** Turno en que ocurrió (puede ser distinto al del pedido, por ejemplo un cobro al turno siguiente). */
    @Column(name = "turno_id")
    private Long turnoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private TipoEvento tipo;

    @Column(nullable = false, length = 500)
    private String detalle;

    @Builder.Default
    @Column(nullable = false)
    private boolean alerta = false;

    /** Quién lo hizo. Vacío cuando fue el cliente desde la web. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
