package com.project.ocumare_food.model.enums;

public enum EstadoPedido {
    /** Mesa abierta o pedido en edición: todavía se pueden agregar productos. */
    ABIERTO,
    EN_PREPARACION,
    LISTO,
    /** Entregado al cliente; puede seguir pendiente de pago (cuenta de mesa). */
    ENTREGADO,
    /**
     * Cerrado: entregado y pagado por completo. Se llega solo cuando se cumplen las dos cosas
     * (un pedido para llevar pagado por adelantado sigue su curso en cocina hasta entregarse).
     * El recaudo se calcula siempre desde la tabla de pagos, no desde este estado.
     */
    PAGADO,
    ANULADO
}
