package com.project.ocumare_food.pedidos.dto;

import com.project.ocumare_food.pedidos.model.DetallePedido;
import com.project.ocumare_food.pedidos.model.EventoPedido;
import com.project.ocumare_food.pedidos.model.TipoEvento;
import com.project.ocumare_food.pedidos.model.Pago;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.moneda.model.Moneda;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.pedidos.model.TipoPedido;
import com.project.ocumare_food.turnos.model.EstadoTurno;
import com.project.ocumare_food.turnos.model.TipoTurno;
import com.project.ocumare_food.usuarios.model.Rol;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Pedidos tomados por el personal, pedidos web y pagos. */
public final class PedidoDto {

    private PedidoDto() {
    }

    public static final int MAX_CANTIDAD = 50;
    public static final int MAX_LINEAS = 40;

    public record ItemRequest(
            @NotNull Long productoId,
            @NotNull @Min(1) @Max(MAX_CANTIDAD) Integer cantidad,
            @Size(max = 200) String notas) {
    }

    /** Pedido tomado por un mesero / cajero. tipo WEB no se permite aquí. */
    public record CrearPedidoRequest(
            @NotNull TipoPedido tipo,
            Long mesaId,
            @Size(max = 100) String clienteNombre,
            @Size(max = 25) String clienteTelefono,
            @Size(max = 300) String direccionEntrega,
            @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal costoDeliveryUsd,
            @Size(max = 500) String notas,
            @NotEmpty @Size(max = MAX_LINEAS) List<@Valid ItemRequest> items) {
    }

    public record CambiarEstadoRequest(@NotNull EstadoPedido estado) {
    }

    public record AnularRequest(@NotBlank @Size(max = 300) String motivo) {
    }

    public record PagoRequest(
            @NotNull MetodoPago metodo,
            @NotNull @DecimalMin(value = "0.01", message = "debe ser mayor que 0")
            @Digits(integer = 14, fraction = 2) BigDecimal monto,
            @Size(max = 60) String referencia) {
    }

    // ---------- Web ----------

    public enum EntregaWeb { DELIVERY, RETIRO }

    public record PedidoWebRequest(
            /** Generada por la página (p. ej. un UUID). Si el cliente reintenta, llega la misma. */
            @NotBlank @Size(min = 8, max = 64) @Pattern(regexp = "^[A-Za-z0-9-]+$") String claveExterna,
            @NotBlank @Size(max = 100) String clienteNombre,
            @NotBlank @Size(min = 7, max = 25) String clienteTelefono,
            @NotNull EntregaWeb entrega,
            @Size(max = 300) String direccionEntrega,
            @Size(max = 500) String notas,
            /** Forma de pago que el cliente anuncia (se confirma al cobrar). */
            @Size(max = 40) String pago,
            /** Campo trampa contra bots: una persona nunca lo llena. */
            String website,
            @NotEmpty @Size(max = MAX_LINEAS) List<@Valid ItemRequest> items) {
    }

    /** Lo que ve el cliente web. No expone datos internos. */
    public record PedidoWebResponse(Integer numero, EstadoPedido estado, BigDecimal totalUsd, BigDecimal totalBs) {
        public static PedidoWebResponse de(Pedido p) {
            return new PedidoWebResponse(p.getNumeroDiario(), p.getEstado(), p.getTotalUsd(), p.getTotalBs());
        }
    }

    // ---------- Respuestas ----------

    public record DetalleResponse(Long id, Long productoId, String nombre, int cantidad,
                                  BigDecimal precioUnitarioUsd, BigDecimal subtotalUsd, String notas,
                                  boolean enviadoCocina) {
        static DetalleResponse de(DetallePedido d) {
            return new DetalleResponse(d.getId(), d.getProducto().getId(), d.getNombreProducto(), d.getCantidad(),
                    d.getPrecioUnitarioUsd(), d.getSubtotalUsd(), d.getNotas(), d.isEnviadoCocina());
        }
    }

    /** Una línea de la bitácora. {@code usuario} es null cuando lo hizo el cliente desde la web. */
    public record EventoResponse(Long id, Long pedidoId, Integer numeroPedido, TipoPedido tipoPedido, Integer mesaNumero,
                                 TipoEvento tipo, String detalle, boolean alerta, String usuario,
                                 LocalDateTime creadoEn) {
        public static EventoResponse de(EventoPedido e) {
            Pedido p = e.getPedido();
            return new EventoResponse(e.getId(), p.getId(), p.getNumeroDiario(), p.getTipo(),
                    p.getMesa() == null ? null : p.getMesa().getNumero(),
                    e.getTipo(), e.getDetalle(), e.isAlerta(),
                    e.getUsuario() == null ? null : e.getUsuario().getNombre(), e.getCreadoEn());
        }
    }

    public record PagoResponse(Long id, MetodoPago metodo, Moneda moneda, BigDecimal monto,
                               BigDecimal tasaBsPorUsd, BigDecimal equivalenteUsd, String referencia,
                               LocalDateTime registradoEn) {
        static PagoResponse de(Pago p) {
            return new PagoResponse(p.getId(), p.getMetodo(), p.getMoneda(), p.getMonto(), p.getTasaBsPorUsd(),
                    p.getEquivalenteUsd(), p.getReferencia(), p.getRegistradoEn());
        }
    }

    public record PedidoResponse(Long id, Integer numeroDiario, TipoPedido tipo, EstadoPedido estado,
                                 Long turnoId, Integer mesaNumero, Long mesaId, String mesero,
                                 String clienteNombre, String clienteTelefono, String direccionEntrega,
                                 String notas, String motivoAnulacion,
                                 BigDecimal tasaBsPorUsd, BigDecimal subtotalUsd, BigDecimal costoDeliveryUsd,
                                 BigDecimal totalUsd, BigDecimal totalBs,
                                 BigDecimal pagadoUsd, BigDecimal saldoPendienteUsd, BigDecimal saldoPendienteBs,
                                 LocalDateTime creadoEn, LocalDateTime cerradoEn,
                                 List<DetalleResponse> detalles, List<PagoResponse> pagos) {

        public static PedidoResponse de(Pedido p) {
            BigDecimal saldo = p.getSaldoPendienteUsd();
            return new PedidoResponse(p.getId(), p.getNumeroDiario(), p.getTipo(), p.getEstado(),
                    p.getTurno().getId(),
                    p.getMesa() == null ? null : p.getMesa().getNumero(),
                    p.getMesa() == null ? null : p.getMesa().getId(),
                    p.getMesero() == null ? null : p.getMesero().getNombre(),
                    p.getClienteNombre(), p.getClienteTelefono(), p.getDireccionEntrega(),
                    p.getNotas(), p.getMotivoAnulacion(),
                    p.getTasaBsPorUsd(), p.getSubtotalUsd(), p.getCostoDeliveryUsd(), p.getTotalUsd(), p.getTotalBs(),
                    p.getPagadoUsd(), saldo,
                    p.getTasaBsPorUsd() == null ? null
                            : com.project.ocumare_food.compartido.Dinero.usdABs(saldo, p.getTasaBsPorUsd()),
                    p.getCreadoEn(), p.getCerradoEn(),
                    p.getDetalles().stream().map(DetalleResponse::de).toList(),
                    p.getPagos().stream().map(PagoResponse::de).toList());
        }
    }

    /**
     * Versión corta para listas (tablero de pedidos del turno).
     * {@code entrega}: solo en pedidos web, "DELIVERY" si trae dirección o "RETIRO" si lo buscan en el local.
     */
    public record PedidoResumen(Long id, Integer numeroDiario, TipoPedido tipo, EstadoPedido estado,
                                Integer mesaNumero, String mesero, String clienteNombre,
                                BigDecimal totalUsd, BigDecimal totalBs, LocalDateTime creadoEn,
                                String entrega) {
        public static PedidoResumen de(Pedido p) {
            String entrega = p.getTipo() != TipoPedido.WEB ? null
                    : (p.getDireccionEntrega() != null ? "DELIVERY" : "RETIRO");
            return new PedidoResumen(p.getId(), p.getNumeroDiario(), p.getTipo(), p.getEstado(),
                    p.getMesa() == null ? null : p.getMesa().getNumero(),
                    p.getMesero() == null ? null : p.getMesero().getNombre(),
                    p.getClienteNombre(), p.getTotalUsd(), p.getTotalBs(), p.getCreadoEn(), entrega);
        }
    }
}
