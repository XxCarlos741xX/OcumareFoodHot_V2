package com.project.ocumare_food.pedidos.model;

import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.usuarios.model.Usuario;

import com.project.ocumare_food.compartido.Dinero;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido de cualquier canal: mesa, para llevar, delivery o web.
 * Guarda la tasa con la que se valorizó, así los totales en Bs de un pedido viejo
 * no cambian cuando cambia la tasa del día.
 */
@Entity
@Table(name = "pedidos",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_pedido_numero_diario", columnNames = {"fecha_operativa", "numero_diario"}),
                @UniqueConstraint(name = "uk_pedido_clave_externa", columnNames = {"clave_externa"})
        },
        indexes = {
                @Index(name = "ix_pedido_turno", columnList = "turno_id"),
                @Index(name = "ix_pedido_estado", columnList = "estado"),
                @Index(name = "ix_pedido_mesa_estado", columnList = "mesa_id, estado")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número corto que ve el cliente y la cocina (#1, #2…). Reinicia cada día operativo. */
    @Column(name = "numero_diario", nullable = false)
    private Integer numeroDiario;

    @Column(name = "fecha_operativa", nullable = false)
    private LocalDate fechaOperativa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private TipoPedido tipo;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPedido estado = EstadoPedido.ABIERTO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    /** Solo para tipo MESA. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesa_id")
    private Mesa mesa;

    /** Quién tomó el pedido. Vacío en pedidos WEB (los hizo el cliente). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mesero_id")
    private Usuario mesero;

    // ---- Datos del cliente (delivery, para llevar y web) ----

    @Column(name = "cliente_nombre", length = 100)
    private String clienteNombre;

    @Column(name = "cliente_telefono", length = 25)
    private String clienteTelefono;

    @Column(name = "direccion_entrega", length = 300)
    private String direccionEntrega;

    @Column(length = 500)
    private String notas;

    @Column(name = "motivo_anulacion", length = 300)
    private String motivoAnulacion;

    /**
     * Clave de idempotencia de la web: si el cliente reintenta el envío,
     * llega la misma clave y no se duplica el pedido.
     */
    @Column(name = "clave_externa", length = 64)
    private String claveExterna;

    // ---- Montos ----

    /**
     * Bs por USD usada para valorizar este pedido (copia de la última tasa registrada al crearlo).
     * Puede quedar vacía si nunca se ha registrado una tasa: el pedido vale solo en USD
     * hasta que se cobre en bolívares, momento en que se le asigna la tasa vigente.
     */
    @Column(name = "tasa_bs_por_usd", precision = 14, scale = 4)
    private BigDecimal tasaBsPorUsd;

    @Builder.Default
    @Column(name = "subtotal_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalUsd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "costo_delivery_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoDeliveryUsd = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalUsd = BigDecimal.ZERO;

    /** Vacío mientras el pedido no tenga tasa. */
    @Column(name = "total_bs", precision = 16, scale = 2)
    private BigDecimal totalBs;

    // ---- Fechas ----

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "cerrado_en")
    private LocalDateTime cerradoEn;

    // ---- Hijos ----

    @Builder.Default
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetallePedido> detalles = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<Pago> pagos = new ArrayList<>();

    // ---- Lógica de montos ----

    public void agregarDetalle(DetallePedido detalle) {
        detalle.setPedido(this);
        detalles.add(detalle);
        recalcularTotales();
    }

    public void quitarDetalle(DetallePedido detalle) {
        detalles.remove(detalle);
        detalle.setPedido(null);
        recalcularTotales();
    }

    public void agregarPago(Pago pago) {
        pago.setPedido(this);
        pagos.add(pago);
    }

    /** Suma los detalles y convierte a Bs con la tasa guardada en el pedido. */
    public void recalcularTotales() {
        subtotalUsd = Dinero.monto(detalles.stream()
                .map(DetallePedido::getSubtotalUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        totalUsd = Dinero.monto(subtotalUsd.add(costoDeliveryUsd == null ? BigDecimal.ZERO : costoDeliveryUsd));
        totalBs = tasaBsPorUsd == null ? null : Dinero.usdABs(totalUsd, tasaBsPorUsd);
    }

    /** Asigna la tasa (si aún no tenía) y recalcula el total en Bs. */
    public void asignarTasa(BigDecimal tasa) {
        this.tasaBsPorUsd = tasa;
        recalcularTotales();
    }

    /** Lo pagado hasta ahora, expresado en USD (suma pagos en $ y en Bs convertidos). */
    public BigDecimal getPagadoUsd() {
        return Dinero.monto(pagos.stream()
                .map(Pago::getEquivalenteUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public BigDecimal getSaldoPendienteUsd() {
        return Dinero.monto(totalUsd.subtract(getPagadoUsd()));
    }
}
