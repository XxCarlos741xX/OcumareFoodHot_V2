package com.project.ocumare_food.salon;

import java.util.Map;

/**
 * Qué mesas están ocupadas. El salón no sabe de pedidos: el módulo pedidos implementa
 * esta interfaz (inversión de dependencias), así salon no depende de pedidos y no hay ciclo.
 */
public interface OcupacionMesas {

    /** mesaId → id del pedido abierto que la ocupa. */
    Map<Long, Long> mesasOcupadas();

    boolean ocupada(Long mesaId);
}
