package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.Mesa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MesaRepository extends JpaRepository<Mesa, Long> {
    List<Mesa> findByActivaTrueOrderByOrdenAscNumeroAsc();
    List<Mesa> findAllByOrderByOrdenAscNumeroAsc();
    boolean existsByNumero(Integer numero);
    boolean existsByNumeroAndIdNot(Integer numero, Long id);

    @Query("select coalesce(max(m.orden), 0) from Mesa m")
    int maxOrden();
}
