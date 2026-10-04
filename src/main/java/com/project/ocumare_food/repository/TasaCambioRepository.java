package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.TasaCambio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TasaCambioRepository extends JpaRepository<TasaCambio, Long> {
    /** La tasa vigente en un momento dado: la última registrada hasta ese instante. */
    Optional<TasaCambio> findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(LocalDateTime momento);

    /** La última tasa registrada. Se ordena por id para no depender de zonas horarias del servidor. */
    Optional<TasaCambio> findFirstByOrderByIdDesc();

    Optional<TasaCambio> findFirstByFuenteOrderByIdDesc(String fuente);

    List<TasaCambio> findTop60ByOrderByVigenteDesdeDescIdDesc();
}
