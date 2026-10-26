package com.project.ocumare_food.inventario.repository;

import com.project.ocumare_food.inventario.model.MovimientoInventario;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovimientoRepository extends JpaRepository<MovimientoInventario, Long> {

    @EntityGraph(attributePaths = "insumo")
    List<MovimientoInventario> findByInsumoIdOrderByIdDesc(Long insumoId, Pageable pagina);

    @EntityGraph(attributePaths = "insumo")
    List<MovimientoInventario> findAllByOrderByIdDesc(Pageable pagina);

    @EntityGraph(attributePaths = "insumo")
    List<MovimientoInventario> findByPedidoIdOrderByIdAsc(Long pedidoId);
}
