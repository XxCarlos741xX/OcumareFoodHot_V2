package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.CatalogoDto.*;
import com.project.ocumare_food.service.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Menú y mesas. Lectura: cualquier usuario con sesión. Escritura: GERENTE y ADMIN (ver SecurityConfig).
 * DELETE desactiva (baja lógica), no borra.
 */
@RestController
@RequestMapping("/api")
public class CatalogoController {

    private final CatalogoService catalogo;

    public CatalogoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    // ---------- Categorías ----------

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return catalogo.listarCategorias();
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crearCategoria(@Valid @RequestBody CategoriaRequest req) {
        return catalogo.crearCategoria(req);
    }

    @PutMapping("/categorias/{id}")
    public CategoriaResponse editarCategoria(@PathVariable Long id, @Valid @RequestBody CategoriaRequest req) {
        return catalogo.editarCategoria(id, req);
    }

    @DeleteMapping("/categorias/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarCategoria(@PathVariable Long id) {
        catalogo.desactivarCategoria(id);
    }

    // ---------- Productos ----------

    @GetMapping("/productos")
    public List<ProductoResponse> productos(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return catalogo.listarProductos(incluirInactivos);
    }

    @GetMapping("/productos/{id}")
    public ProductoResponse producto(@PathVariable Long id) {
        return catalogo.obtenerProducto(id);
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoResponse crearProducto(@Valid @RequestBody ProductoRequest req) {
        return catalogo.crearProducto(req);
    }

    @PutMapping("/productos/{id}")
    public ProductoResponse editarProducto(@PathVariable Long id, @Valid @RequestBody ProductoRequest req) {
        return catalogo.editarProducto(id, req);
    }

    /** Marcar agotado / disponible. */
    @PatchMapping("/productos/{id}/disponible")
    public ProductoResponse disponible(@PathVariable Long id, @Valid @RequestBody DisponibleRequest req) {
        return catalogo.cambiarDisponible(id, req.disponible());
    }

    @PostMapping("/productos/{id}/reactivar")
    public ProductoResponse reactivar(@PathVariable Long id) {
        return catalogo.reactivarProducto(id);
    }

    @DeleteMapping("/productos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarProducto(@PathVariable Long id) {
        catalogo.desactivarProducto(id);
    }

    // ---------- Mesas ----------

    @GetMapping("/mesas")
    public List<MesaResponse> mesas(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return catalogo.listarMesas(incluirInactivas);
    }

    @PostMapping("/mesas")
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponse crearMesa(@Valid @RequestBody MesaRequest req) {
        return catalogo.crearMesa(req);
    }

    @PutMapping("/mesas/{id}")
    public MesaResponse editarMesa(@PathVariable Long id, @Valid @RequestBody MesaRequest req) {
        return catalogo.editarMesa(id, req);
    }

    /** Guardar el orden: { "ids": [5, 2, 7, ...] } */
    @PutMapping("/mesas/orden")
    public List<MesaResponse> ordenarMesas(@Valid @RequestBody OrdenMesasRequest req) {
        return catalogo.ordenarMesas(req.ids());
    }

    /** Renumerar 1..n las mesas activas según su orden. */
    @PostMapping("/mesas/renumerar")
    public List<MesaResponse> renumerar() {
        return catalogo.renumerarMesas();
    }

    @DeleteMapping("/mesas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarMesa(@PathVariable Long id) {
        catalogo.desactivarMesa(id);
    }
}
