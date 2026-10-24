package com.project.ocumare_food.turnos.controller;

import com.project.ocumare_food.turnos.TurnoService;
import com.project.ocumare_food.turnos.dto.TurnoDto.AbrirTurnoRequest;
import com.project.ocumare_food.turnos.dto.TurnoDto.TurnoResponse;
import com.project.ocumare_food.usuarios.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Turnos. Ver: todos. Abrir: GERENTE y ADMIN (ver SecurityConfig). Cerrar: módulo caja. */
@RestController
@RequestMapping("/api")
public class TurnoController {

    private final TurnoService turnos;

    public TurnoController(TurnoService turnos) {
        this.turnos = turnos;
    }

    @GetMapping("/turnos/actual")
    public TurnoResponse turnoActual() {
        return turnos.turnoActual();
    }

    @PostMapping("/turnos/abrir")
    @ResponseStatus(HttpStatus.CREATED)
    public TurnoResponse abrirTurno(@Valid @RequestBody AbrirTurnoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return turnos.abrirTurno(req, UsuarioActual.de(jwt));
    }
}
