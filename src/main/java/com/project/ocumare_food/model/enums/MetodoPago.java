package com.project.ocumare_food.model.enums;

/** Cada método tiene una moneda fija, así el corte de caja sabe en qué moneda sumar. */
public enum MetodoPago {
    EFECTIVO_USD(Moneda.USD),
    ZELLE(Moneda.USD),
    EFECTIVO_BS(Moneda.BS),
    PAGO_MOVIL(Moneda.BS),
    PUNTO_DE_VENTA(Moneda.BS),
    TRANSFERENCIA_BS(Moneda.BS);

    private final Moneda moneda;

    MetodoPago(Moneda moneda) {
        this.moneda = moneda;
    }

    public Moneda getMoneda() {
        return moneda;
    }
}
