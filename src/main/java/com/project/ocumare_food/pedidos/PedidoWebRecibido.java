package com.project.ocumare_food.pedidos;

/**
 * Evento que publica el módulo pedidos al guardar un pedido de la página web.
 * Lo escucha, por ejemplo, el módulo notificaciones (aviso a Telegram) después del commit.
 */
public record PedidoWebRecibido(Long pedidoId) {
}
