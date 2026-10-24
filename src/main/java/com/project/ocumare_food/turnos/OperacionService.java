package com.project.ocumare_food.turnos;

import com.project.ocumare_food.pedidos.service.PedidoService;

import com.project.ocumare_food.turnos.dto.OperacionDto.*;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.moneda.model.TasaCambio;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.turnos.model.EstadoTurno;
import com.project.ocumare_food.moneda.repository.TasaCambioRepository;
import com.project.ocumare_food.turnos.repository.TurnoRepository;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import com.project.ocumare_food.usuarios.UsuarioActual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tasa de cambio y apertura de turnos.
 * La tasa NO es obligatoria para operar: si no se registró la del día se usa la última conocida,
 * y si nunca hubo una, los pedidos van solo en USD (ver PedidoService).
 */
@Service
public class OperacionService {

    private final TasaCambioRepository tasas;
    private final TurnoRepository turnos;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public OperacionService(TasaCambioRepository tasas, TurnoRepository turnos, UsuarioRepository usuarios, Clock reloj) {
        this.tasas = tasas;
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    // ===================== Tasa =====================

    @Transactional
    public TasaResponse registrarTasa(TasaRequest req, UsuarioActual actor) {
        TasaCambio t = TasaCambio.builder()
                .bsPorUsd(req.bsPorUsd().setScale(4, RoundingMode.HALF_UP))
                .vigenteDesde(LocalDateTime.now(reloj))
                .fuente(req.fuente() == null || req.fuente().isBlank() ? "Manual" : req.fuente().trim())
                .registradaPor(usuarios.getReferenceById(actor.id()))
                .build();
        return TasaResponse.de(tasas.save(t));
    }

    /** Nombre con que se guardan las tasas que trae el sistema solo. */
    public static final String FUENTE_BCV_AUTO = "BCV automática";

    /**
     * Registra la tasa del BCV traída por el sistema, solo si el BCV publicó un valor distinto
     * al último que trajo. Así, si el gerente pone una tasa a mano, se respeta hasta que el BCV
     * publique la siguiente. Rechaza saltos de más del 50 % (señal de un dato malo del servicio).
     */
    @Transactional
    public Optional<TasaResponse> registrarTasaBcv(BigDecimal valor) {
        BigDecimal tasa = valor.setScale(4, RoundingMode.HALF_UP);
        Optional<TasaCambio> ultimaBcv = tasas.findFirstByFuenteOrderByIdDesc(FUENTE_BCV_AUTO);
        if (ultimaBcv.isPresent() && ultimaBcv.get().getBsPorUsd().compareTo(tasa) == 0) {
            return Optional.empty();
        }
        Optional<BigDecimal> vigente = tasaVigente();
        if (vigente.isPresent()) {
            BigDecimal cambio = tasa.subtract(vigente.get()).abs().divide(vigente.get(), 4, RoundingMode.HALF_UP);
            if (cambio.compareTo(new BigDecimal("0.5")) > 0) {
                throw ErrorNegocio.conflicto("La tasa del BCV (" + tasa.toPlainString() + ") difiere más de 50 % de la actual; ponla a mano si es correcta");
            }
        }
        TasaCambio t = TasaCambio.builder()
                .bsPorUsd(tasa)
                .vigenteDesde(LocalDateTime.now(reloj))
                .fuente(FUENTE_BCV_AUTO)
                .build();
        return Optional.of(TasaResponse.de(tasas.save(t)));
    }

    /** Última tasa registrada (no importa de qué día). Vacío si nunca se registró ninguna. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> tasaVigente() {
        return ultimaTasa().map(TasaCambio::getBsPorUsd);
    }

    @Transactional(readOnly = true)
    public TasaResponse tasaVigenteDetalle() {
        return ultimaTasa().map(TasaResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Todavía no se ha registrado ninguna tasa"));
    }

    @Transactional(readOnly = true)
    public List<TasaResponse> historialTasas() {
        return tasas.findTop60ByOrderByVigenteDesdeDescIdDesc().stream().map(TasaResponse::de).toList();
    }

    private Optional<TasaCambio> ultimaTasa() {
        return tasas.findFirstByOrderByIdDesc();
    }

    // ===================== Turnos =====================

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
                .abiertoPor(usuarios.getReferenceById(actor.id()))
                .fondoInicialUsd(com.project.ocumare_food.compartido.Dinero.monto(req.fondoInicialUsd()))
                .fondoInicialBs(com.project.ocumare_food.compartido.Dinero.monto(req.fondoInicialBs()))
                .build();
        turnos.saveAndFlush(t);
        return TurnoResponse.de(turnos.findById(t.getId()).orElseThrow());
    }

    @Transactional(readOnly = true)
    public TurnoResponse turnoActual() {
        return turnos.findFirstByEstado(EstadoTurno.ABIERTO).map(TurnoResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No hay ningún turno abierto"));
    }
}
