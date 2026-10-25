package com.project.ocumare_food.pedidos.repository;

import com.project.ocumare_food.pedidos.model.EventoPedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoPedidoRepository extends JpaRepository<EventoPedido, Long> {

    @EntityGraph(attributePaths = {"usuario", "pedido", "pedido.mesa"})
    List<EventoPedido> findByPedidoIdOrderByIdAsc(Long pedidoId);

    @EntityGraph(attributePaths = {"usuario", "pedido", "pedido.mesa"})
    List<EventoPedido> findByTurnoIdOrderByIdDesc(Long turnoId);

    @EntityGraph(attributePaths = {"usuario", "pedido", "pedido.mesa"})
    List<EventoPedido> findByTurnoIdAndAlertaTrueOrderByIdDesc(Long turnoId);
}
