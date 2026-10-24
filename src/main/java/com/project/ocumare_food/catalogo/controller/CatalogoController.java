package com.project.ocumare_food.catalogo.controller;

import com.project.ocumare_food.catalogo.dto.CatalogoDto.*;
import com.project.ocumare_food.catalogo.CatalogoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Categorías y productos. Lectura: cualquier usuario con sesión. Escritura: GERENTE y ADMIN (ver SecurityConfig).
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
}
