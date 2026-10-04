package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.OperacionDto.*;
import com.project.ocumare_food.security.UsuarioActual;
import com.project.ocumare_food.exception.ErrorNegocio;
import com.project.ocumare_food.service.ConsultaBcv;
import com.project.ocumare_food.service.OperacionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Tasa de cambio y turnos. Ver/consultar: todos. Registrar/abrir: GERENTE y ADMIN. */
@RestController
@RequestMapping("/api")
public class OperacionController {

    private final OperacionService operacion;
    private final ConsultaBcv bcv;

    public OperacionController(OperacionService operacion, ConsultaBcv bcv) {
        this.operacion = operacion;
        this.bcv = bcv;
    }

    /** nueva = false cuando el BCV no ha cambiado y se devuelve la tasa vigente. */
    public record TasaBcvResponse(boolean nueva, TasaResponse tasa) {
    }

    @GetMapping("/tasas/vigente")
    public TasaResponse tasaVigente() {
        return operacion.tasaVigenteDetalle();
    }

    @GetMapping("/tasas")
    public List<TasaResponse> historial() {
        return operacion.historialTasas();
    }

    @PostMapping("/tasas")
    @ResponseStatus(HttpStatus.CREATED)
    public TasaResponse registrarTasa(@Valid @RequestBody TasaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return operacion.registrarTasa(req, UsuarioActual.de(jwt));
    }

    /** "Traer tasa del BCV" desde el POS: consulta ya y la registra (o confirma que no cambió). */
    @PostMapping("/tasas/bcv")
    public TasaBcvResponse traerTasaBcv() {
        BigDecimal valor = bcv.tasaOficial().orElseThrow(() -> new ErrorNegocio(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar la tasa del BCV en este momento. Intenta más tarde o ponla a mano."));
        return operacion.registrarTasaBcv(valor)
                .map(t -> new TasaBcvResponse(true, t))
                .orElseGet(() -> new TasaBcvResponse(false, operacion.tasaVigenteDetalle()));
    }

    @GetMapping("/turnos/actual")
    public TurnoResponse turnoActual() {
        return operacion.turnoActual();
    }

    @PostMapping("/turnos/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public TurnoResponse abrirTurno(@Valid @RequestBody AbrirTurnoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return operacion.abrirTurno(req, UsuarioActual.de(jwt));
    }
}
