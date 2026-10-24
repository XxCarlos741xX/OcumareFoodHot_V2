package com.project.ocumare_food.caja.controller;

import com.project.ocumare_food.caja.dto.CorteDto.*;
import com.project.ocumare_food.turnos.dto.OperacionDto.TurnoResponse;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.caja.service.CorteService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Cortes de turno y dashboard. Solo GERENTE y ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api")
public class CorteController {

    private final CorteService cortes;

    public CorteController(CorteService cortes) {
        this.cortes = cortes;
    }

    /** Cierra el turno abierto. Cuerpo opcional: { "conteo": {efectivoUsd, efectivoBs}, "observaciones": "..." } */
    @PostMapping("/turnos/cerrar")
    public CorteTurno cerrar(@Valid @RequestBody(required = false) CerrarTurnoRequest req,
                             @AuthenticationPrincipal Jwt jwt) {
        return cortes.cerrarTurno(req, UsuarioActual.de(jwt));
    }

    /** Registrar o corregir el conteo de caja de un turno cerrado. */
    @PutMapping("/turnos/{id}/conteo")
    public CorteTurno conteo(@PathVariable Long id, @Valid @RequestBody ConteoRequest req,
                             @AuthenticationPrincipal Jwt jwt) {
        return cortes.registrarConteo(id, req, UsuarioActual.de(jwt));
    }

    /** Corte de un turno (si está abierto, es una vista previa en vivo). */
    @GetMapping("/turnos/{id}/corte")
    public CorteTurno corte(@PathVariable Long id) {
        return cortes.corte(id);
    }

    @GetMapping("/turnos")
    public List<TurnoResponse> historial(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cortes.historial(desde, hasta);
    }

    /** Resumen del día (por defecto hoy): totales, por turno, por método y por mesero. */
    @GetMapping("/dashboard/dia")
    public DashboardDia dia(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return cortes.dashboardDia(fecha);
    }

    /** Serie diaria de recaudo para la gráfica (por defecto los últimos 7 días). */
    @GetMapping("/dashboard/rango")
    public DashboardRango rango(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return cortes.dashboardRango(desde, hasta);
    }
}
