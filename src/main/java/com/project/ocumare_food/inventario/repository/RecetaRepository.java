package com.project.ocumare_food.inventario.repository;

import com.project.ocumare_food.inventario.model.RecetaLinea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface RecetaRepository extends JpaRepository<RecetaLinea, Long> {

    @EntityGraph(attributePaths = "insumo")
    List<RecetaLinea> findByProductoIdOrderByIdAsc(Long productoId);

    @EntityGraph(attributePaths = "insumo")
    List<RecetaLinea> findByProductoIdIn(Collection<Long> productoIds);

    @EntityGraph(attributePaths = "insumo")
    List<RecetaLinea> findByInsumoIdIn(Collection<Long> insumoIds);

    @EntityGraph(attributePaths = "insumo")
    List<RecetaLinea> findAllByOrderByProductoIdAscIdAsc();

    void deleteByProductoId(Long productoId);
}
