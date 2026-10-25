package com.project.ocumare_food.cocina.controller;

import com.project.ocumare_food.cocina.CocinaService;
import com.project.ocumare_food.cocina.dto.CocinaDto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Impresoras de cocina. Solo GERENTE y ADMIN (ver SecurityConfig). */
@RestController
@RequestMapping("/api/impresoras")
public class ImpresoraController {

    private final CocinaService cocina;

    public ImpresoraController(CocinaService cocina) {
        this.cocina = cocina;
    }

    @GetMapping
    public List<ImpresoraResponse> listar() {
        return cocina.impresoras();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImpresoraCreada crear(@Valid @RequestBody CrearImpresoraRequest req) {
        return cocina.crearImpresora(req);
    }

    @DeleteMapping("/{id}")
    public ImpresoraResponse desactivar(@PathVariable Long id) {
        return cocina.desactivar(id);
    }

    @PostMapping("/prueba")
    @ResponseStatus(HttpStatus.CREATED)
    public ComandaResponse prueba() {
        return cocina.prueba();
    }
}
