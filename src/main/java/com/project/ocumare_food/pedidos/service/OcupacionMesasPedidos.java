package com.project.ocumare_food.pedidos.service;

import com.project.ocumare_food.pedidos.repository.PedidoRepository;
import com.project.ocumare_food.salon.OcupacionMesas;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/** Una mesa está ocupada mientras tenga un pedido abierto (ver PedidoRepository.ESTADOS_ABIERTOS). */
@Component
@Transactional(readOnly = true)
public class OcupacionMesasPedidos implements OcupacionMesas {

    private final PedidoRepository pedidos;

    public OcupacionMesasPedidos(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    public Map<Long, Long> mesasOcupadas() {
        Map<Long, Long> ocupadas = new HashMap<>();
        for (Object[] fila : pedidos.mesasOcupadas(PedidoRepository.ESTADOS_ABIERTOS)) {
            ocupadas.put((Long) fila[0], (Long) fila[1]);
        }
        return ocupadas;
    }

    @Override
    public boolean ocupada(Long mesaId) {
        return !pedidos.findByMesaIdAndEstadoIn(mesaId, PedidoRepository.ESTADOS_ABIERTOS).isEmpty();
    }
}
