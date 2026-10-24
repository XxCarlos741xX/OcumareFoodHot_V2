package com.project.ocumare_food.pedidos;

import com.project.ocumare_food.pedidos.ResumenVentas.*;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.pedidos.repository.PagoRepository;
import com.project.ocumare_food.pedidos.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * API pública de consulta del módulo pedidos, para los demás módulos (caja, notificaciones).
 * Solo lectura: nadie fuera de pedidos toca PedidoRepository ni PagoRepository.
 *
 * Reglas del recaudo: todo el dinero que entró se calcula desde los pagos, por el turno en que entró.
 * La venta (valor de pedidos) se cuenta en el turno en que se creó el pedido.
 */
@Service
@Transactional(readOnly = true)
public class ConsultasPedidos {

    private final PedidoRepository pedidos;
    private final PagoRepository pagos;

    public ConsultasPedidos(PedidoRepository pedidos, PagoRepository pagos) {
        this.pedidos = pedidos;
        this.pagos = pagos;
    }

    public Optional<Pedido> buscar(Long id) {
        return pedidos.findById(id);
    }

    public List<PorEstado> resumenPorEstado(Collection<Long> turnos) {
        return pedidos.resumenPorEstado(turnos);
    }

    public List<VentaPorMesero> ventaPorMesero(Collection<Long> turnos) {
        return pedidos.ventaPorMesero(turnos);
    }

    public List<PorMetodo> recaudoPorMetodo(Collection<Long> turnos) {
        return pagos.recaudoPorMetodo(turnos);
    }

    /** El cobro se atribuye al mesero dueño del pedido (los pedidos web quedan sin mesero). */
    public List<PorMeseroMoneda> recaudoPorMesero(Collection<Long> turnos) {
        return pagos.recaudoPorMesero(turnos);
    }

    public List<PorDiaMoneda> recaudoPorDia(LocalDate desde, LocalDate hasta) {
        return pagos.recaudoPorDia(desde, hasta);
    }
}
