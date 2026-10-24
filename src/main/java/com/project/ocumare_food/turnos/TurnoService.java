package com.project.ocumare_food.turnos;

import com.project.ocumare_food.compartido.Dinero;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.turnos.dto.TurnoDto.AbrirTurnoRequest;
import com.project.ocumare_food.turnos.dto.TurnoDto.TurnoResponse;
import com.project.ocumare_food.turnos.model.EstadoTurno;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.turnos.repository.TurnoRepository;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.usuarios.Usuarios;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Turnos (API pública del módulo turnos). Abrir turno y consultar el turno abierto.
 * Pedidos y caja usan esta clase en vez de TurnoRepository.
 * El cierre con corte de caja está en el módulo caja (CorteService).
 */
@Service
public class TurnoService {

    private final TurnoRepository turnos;
    private final Usuarios usuarios;
    private final Clock reloj;

    public TurnoService(TurnoRepository turnos, Usuarios usuarios, Clock reloj) {
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** Solo puede haber un turno abierto a la vez, y un turno por tipo por día. */
    @Transactional
    public TurnoResponse abrirTurno(AbrirTurnoRequest req, UsuarioActual actor) {
        if (turnos.existsByEstado(EstadoTurno.ABIERTO)) {
            throw ErrorNegocio.conflicto("Ya hay un turno abierto; ciérralo antes de abrir otro");
        }
        LocalDate fecha = req.fechaOperativa() == null ? LocalDate.now(reloj) : req.fechaOperativa();
        if (turnos.existsByFechaOperativaAndTipo(fecha, req.tipo())) {
            throw ErrorNegocio.conflicto("El turno " + req.tipo() + " del " + fecha + " ya existe");
        }
        Turno t = Turno.builder()
                .tipo(req.tipo())
                .fechaOperativa(fecha)
                .abiertoEn(LocalDateTime.now(reloj))
                .abiertoPor(usuarios.referencia(actor.id()))
                .fondoInicialUsd(Dinero.monto(req.fondoInicialUsd()))
                .fondoInicialBs(Dinero.monto(req.fondoInicialBs()))
                .build();
        turnos.saveAndFlush(t);
        return TurnoResponse.de(turnos.findById(t.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public TurnoResponse turnoActual() {
        return turnoAbierto().map(TurnoResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No hay ningún turno abierto"));
    }

    // ===================== Para otros módulos =====================

    /** El turno abierto, si hay. */
    @Transactional
    public Optional<Turno> turnoAbierto() {
        return turnos.findFirstByEstado(EstadoTurno.ABIERTO);
    }

    /**
     * El turno abierto con bloqueo de escritura (debe llamarse dentro de una transacción).
     * Así dos pedidos simultáneos esperan su turno y nunca reciben el mismo número diario,
     * y un cierre no se cruza con un pedido que está entrando.
     */
    @Transactional
    public Optional<Turno> bloquearTurnoAbierto() {
        return turnos.bloquearTurnoAbierto();
    }

    @Transactional
    public Optional<Turno> buscar(Long id) {
        return turnos.findById(id);
    }

    @Transactional
    public List<Turno> delDia(LocalDate fecha) {
        return turnos.findByFechaOperativaOrderByAbiertoEnAsc(fecha);
    }

    @Transactional
    public List<Turno> entre(LocalDate desde, LocalDate hasta) {
        return turnos.findByFechaOperativaBetweenOrderByFechaOperativaDescAbiertoEnDesc(desde, hasta);
    }

    /** Escribe ya en la base los cambios pendientes de los turnos (los usa el corte antes de responder). */
    @Transactional
    public void guardarCambios() {
        turnos.flush();
    }
}
