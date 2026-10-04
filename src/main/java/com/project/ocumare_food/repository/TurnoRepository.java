package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.Turno;
import com.project.ocumare_food.model.enums.EstadoTurno;
import com.project.ocumare_food.model.enums.TipoTurno;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Long> {
    Optional<Turno> findFirstByEstado(EstadoTurno estado);

    boolean existsByEstado(EstadoTurno estado);

    java.util.List<Turno> findByFechaOperativaOrderByAbiertoEnAsc(LocalDate fecha);

    java.util.List<Turno> findByFechaOperativaBetweenOrderByFechaOperativaDescAbiertoEnDesc(LocalDate desde, LocalDate hasta);

    boolean existsByFechaOperativaAndTipo(LocalDate fecha, TipoTurno tipo);

    /**
     * Toma el turno abierto con bloqueo de escritura. Se usa al crear pedidos: así dos pedidos
     * simultáneos esperan su turno y nunca reciben el mismo número diario.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Turno t where t.estado = com.project.ocumare_food.model.enums.EstadoTurno.ABIERTO")
    Optional<Turno> bloquearTurnoAbierto();
}
