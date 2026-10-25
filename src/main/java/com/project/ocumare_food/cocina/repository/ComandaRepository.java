package com.project.ocumare_food.cocina.repository;

import com.project.ocumare_food.cocina.model.Comanda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ComandaRepository extends JpaRepository<Comanda, Long> {

    /** La más vieja que falta por imprimir (y que no está vencida ni abandonada). */
    Optional<Comanda> findFirstByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfterOrderByIdAsc(LocalDateTime desde);

    long countByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfter(LocalDateTime desde);

    Optional<Comanda> findFirstByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfterOrderByCreadaEnAsc(LocalDateTime desde);

    List<Comanda> findByPedidoIdOrderByIdAsc(Long pedidoId);

    List<Comanda> findTop30ByOrderByIdDesc();
}
