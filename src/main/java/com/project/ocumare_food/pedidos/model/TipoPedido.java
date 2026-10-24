package com.project.ocumare_food.pedidos.model;

public enum TipoPedido {
    /** Consumo en el local, asociado a una mesa y a un mesero. */
    MESA,
    /** Se pide en el mostrador y se lo lleva el cliente. */
    PARA_LLEVAR,
    /** Delivery tomado por el personal (teléfono / WhatsApp). */
    DELIVERY,
    /** Pedido hecho por el cliente desde la página web. */
    WEB
}
