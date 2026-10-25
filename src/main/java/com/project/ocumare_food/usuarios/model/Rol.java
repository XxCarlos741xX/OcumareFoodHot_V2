package com.project.ocumare_food.usuarios.model;

/**
 * Roles del sistema, de menor a mayor. En Spring Security son ROLE_MESERO, ROLE_CAJERO, ROLE_GERENTE y ROLE_ADMIN.
 *  - MESERO: toma pedidos de sus mesas y los envía a cocina. No cobra.
 *  - CAJERO: además cobra, acepta los pedidos web, ve y audita todos los pedidos, abre y cierra el turno.
 *  - GERENTE: además maneja la carta, la tasa, las mesas, el personal, las ventas y las anulaciones.
 *  - ADMIN: todo, incluso crear gerentes.
 */
public enum Rol {
    MESERO,
    CAJERO,
    GERENTE,
    ADMIN;

    /** Cajero, gerente o administrador: los que manejan dinero y auditan. */
    public boolean esCaja() {
        return this != MESERO;
    }

    /** Gerente o administrador. */
    public boolean esGerencia() {
        return this == GERENTE || this == ADMIN;
    }
}
