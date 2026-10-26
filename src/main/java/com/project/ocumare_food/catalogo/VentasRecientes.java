package com.project.ocumare_food.catalogo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lo que catalogo necesita saber de las ventas para armar el carrusel "Lo más pedido" de la web.
 * La implementa el módulo pedidos (catalogo no conoce los pedidos: solo pide los ids más vendidos).
 */
public interface VentasRecientes {

    /** Ids de producto ordenados por unidades vendidas (de más a menos) desde esa fecha, sin pedidos anulados. */
    List<Long> masVendidos(LocalDateTime desde, int limite);
}
