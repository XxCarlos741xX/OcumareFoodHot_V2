package com.project.ocumare_food.inventario.controller;

import com.project.ocumare_food.inventario.InventarioService;
import com.project.ocumare_food.inventario.dto.InventarioDto.*;
import com.project.ocumare_food.usuarios.UsuarioActual;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Inventario. Ver y registrar compras o mermas: cajero y gerencia.
 * Crear y editar insumos, recetas y ajustar por conteo: solo gerencia (ver SecurityConfig e InventarioService).
 */
@RestController
@RequestMapping("/api/inventario")
public class InventarioController {

    private final InventarioService inventario;

    public InventarioController(InventarioService inventario) {
        this.inventario = inventario;
    }

    @GetMapping("/insumos")
    public List<InsumoResponse> insumos() {
        return inventario.listarInsumos();
    }

    @PostMapping("/insumos")
    @ResponseStatus(HttpStatus.CREATED)
    public InsumoResponse crear(@Valid @RequestBody InsumoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return inventario.crearInsumo(req, UsuarioActual.de(jwt));
    }

    @PutMapping("/insumos/{id}")
    public InsumoResponse editar(@PathVariable Long id, @Valid @RequestBody InsumoRequest req) {
        return inventario.editarInsumo(id, req);
    }

    @GetMapping("/movimientos")
    public List<MovimientoResponse> movimientos(@RequestParam(required = false) Long insumoId,
                                                @RequestParam(required = false) Integer limite,
                                                @RequestParam(required = false) Long pedidoId) {
        return pedidoId != null ? inventario.movimientosDePedido(pedidoId) : inventario.movimientos(insumoId, limite);
    }

    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public MovimientoResponse registrar(@Valid @RequestBody MovimientoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return inventario.registrar(req, UsuarioActual.de(jwt));
    }

    @GetMapping("/recetas")
    public List<RecetaResponse> recetas() {
        return inventario.recetas();
    }

    @GetMapping("/recetas/{productoId}")
    public RecetaResponse receta(@PathVariable Long productoId) {
        return inventario.receta(productoId);
    }

    @PutMapping("/recetas/{productoId}")
    public RecetaResponse guardarReceta(@PathVariable Long productoId, @Valid @RequestBody RecetaRequest req) {
        return inventario.guardarReceta(productoId, req);
    }

    @GetMapping("/alertas")
    public AlertasInventario alertas() {
        return inventario.alertas();
    }
}
