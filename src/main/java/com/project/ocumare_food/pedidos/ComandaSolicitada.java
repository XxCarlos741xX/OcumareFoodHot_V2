package com.project.ocumare_food.pedidos;

import com.project.ocumare_food.pedidos.model.TipoPedido;

import java.util.List;

/**
 * Evento que publica pedidos cuando la cocina tiene que enterarse de algo: un pedido que entra a cocina,
 * platos que se agregan o se quitan después, una anulación o una reimpresión.
 * Lo escucha el módulo cocina, que arma la comanda y la deja en la cola de impresión.
 * Se publica dentro de la misma transacción: si el cambio del pedido falla, no queda comanda suelta.
 *
 * @param entrega      "DELIVERY" o "RETIRO" para pedidos web y delivery; null en mesa y para llevar.
 * @param atendidoPor  nombre del mesero o cajero que tomó el pedido ("Web" si lo hizo el cliente).
 * @param solicitadoPor quien hizo la acción que generó esta comanda (para la reimpresión o el cancelado).
 */
public record ComandaSolicitada(Long pedidoId, Integer numeroPedido, TipoComanda tipo, TipoPedido tipoPedido,
                                Integer mesaNumero, String clienteNombre, String entrega, String atendidoPor,
                                String notasPedido, List<Linea> lineas, String solicitadoPor) {

    public enum TipoComanda {
        /** El pedido entra a cocina por primera vez. */
        NUEVA,
        /** Platos agregados a un pedido que ya estaba en cocina. */
        ADICIONAL,
        /** Platos quitados después de enviarlos a cocina. */
        CANCELADO,
        /** El pedido completo se anuló mientras estaba en cocina. */
        ANULADO,
        /** Copia de lo que ya se envió (papel perdido o atascado). No es para preparar de nuevo. */
        REIMPRESION
    }

    public record Linea(int cantidad, String nombre, String notas) {
    }
}
