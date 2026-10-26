package com.project.ocumare_food.inventario.repository;

import com.project.ocumare_food.inventario.model.AgotadoPorInventario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgotadoRepository extends JpaRepository<AgotadoPorInventario, Long> {
}
