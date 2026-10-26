package com.project.ocumare_food.inventario.model;

public enum TipoMovimiento {
    /** Compra o llegada de mercancía (suma). */
    ENTRADA,
    /** Consumo por un pedido que entró a cocina (resta, automático). */
    VENTA,
    /** Se devuelve lo de un plato cancelado o un pedido anulado mientras estaba en cocina (suma, automático). */
    DEVOLUCION,
    /** Se dañó, se botó o se perdió (resta). */
    MERMA,
    /** Conteo físico: el stock pasa a ser lo contado (suma o resta la diferencia). */
    AJUSTE
}
