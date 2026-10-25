package com.project.ocumare_food.cocina.repository;

import com.project.ocumare_food.cocina.model.Impresora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImpresoraRepository extends JpaRepository<Impresora, Long> {

    Optional<Impresora> findByTokenHashAndActivaTrue(String tokenHash);

    List<Impresora> findAllByOrderByIdAsc();

    List<Impresora> findByActivaTrue();
}
