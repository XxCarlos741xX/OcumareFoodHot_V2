package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.Producto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    /** Menú visible: activos, con su categoría cargada en la misma consulta. */
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findByActivoTrueOrderByCategoriaOrdenAscNombreAsc();

    /** Menú público: producto activo y categoría activa. */
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findByActivoTrueAndCategoriaActivaTrueOrderByCategoriaOrdenAscNombreAsc();

    @EntityGraph(attributePaths = "categoria")
    List<Producto> findAllByOrderByCategoriaOrdenAscNombreAsc();
}
