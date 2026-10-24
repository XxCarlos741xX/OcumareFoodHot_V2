package com.project.ocumare_food.catalogo.repository;

import com.project.ocumare_food.catalogo.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    List<Categoria> findByActivaTrueOrderByOrdenAsc();
    List<Categoria> findAllByOrderByOrdenAscNombreAsc();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
