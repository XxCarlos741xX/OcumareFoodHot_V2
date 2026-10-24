package com.project.ocumare_food.catalogo.controller;

import com.project.ocumare_food.catalogo.CatalogoService;
import com.project.ocumare_food.catalogo.dto.CatalogoDto.MenuResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Menú público (sin login) que usa la página web de clientes. */
@RestController
@RequestMapping("/api")
public class MenuPublicoController {

    private final CatalogoService catalogo;

    public MenuPublicoController(CatalogoService catalogo) {
        this.catalogo = catalogo;
    }

    @GetMapping("/menu")
    public MenuResponse menu() {
        return catalogo.menuPublico();
    }
}
