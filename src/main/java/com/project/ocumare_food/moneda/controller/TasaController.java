package com.project.ocumare_food.moneda.controller;

import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.moneda.TasaService;
import com.project.ocumare_food.moneda.bcv.ConsultaBcv;
import com.project.ocumare_food.moneda.dto.TasaDto.TasaRequest;
import com.project.ocumare_food.moneda.dto.TasaDto.TasaResponse;
import com.project.ocumare_food.usuarios.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Tasa de cambio. Ver/consultar: todos. Registrar: GERENTE y ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api")
public class TasaController {

    private final TasaService tasas;
    private final ConsultaBcv bcv;

    public TasaController(TasaService tasas, ConsultaBcv bcv) {
        this.tasas = tasas;
        this.bcv = bcv;
    }

    /** nueva = false cuando el BCV no ha cambiado y se devuelve la tasa vigente. */
    public record TasaBcvResponse(boolean nueva, TasaResponse tasa) {
    }

    @GetMapping("/tasas/vigente")
    public TasaResponse tasaVigente() {
        return tasas.tasaVigenteDetalle();
    }

    @GetMapping("/tasas")
    public List<TasaResponse> historial() {
        return tasas.historialTasas();
    }

    @PostMapping("/tasas")
    @ResponseStatus(HttpStatus.CREATED)
    public TasaResponse registrarTasa(@Valid @RequestBody TasaRequest req, @AuthenticationPrincipal Jwt jwt) {
        return tasas.registrarTasa(req, UsuarioActual.de(jwt));
    }

    /** "Traer tasa del BCV" desde el POS: consulta ya y la registra (o confirma que no cambió). */
    @PostMapping("/tasas/bcv")
    public TasaBcvResponse traerTasaBcv() {
        BigDecimal valor = bcv.tasaOficial().orElseThrow(() -> new ErrorNegocio(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo consultar la tasa del BCV en este momento. Intenta más tarde o ponla a mano."));
        return tasas.registrarTasaBcv(valor)
                .map(t -> new TasaBcvResponse(true, t))
                .orElseGet(() -> new TasaBcvResponse(false, tasas.tasaVigenteDetalle()));
    }
}
