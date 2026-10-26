package com.project.ocumare_food.pedidos;

import com.project.ocumare_food.catalogo.VentasRecientes;
import com.project.ocumare_food.pedidos.repository.PedidoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Unidades vendidas por producto, para el carrusel de la web (ver catalogo.VentasRecientes). */
@Component
class MasVendidos implements VentasRecientes {

    private final PedidoRepository pedidos;

    MasVendidos(PedidoRepository pedidos) {
        this.pedidos = pedidos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> masVendidos(LocalDateTime desde, int limite) {
        return pedidos.masVendidos(desde, PageRequest.of(0, Math.max(1, limite)));
    }
}
