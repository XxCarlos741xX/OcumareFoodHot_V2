package com.project.ocumare_food.cocina.controller;

import com.project.ocumare_food.cocina.CocinaService;
import com.project.ocumare_food.cocina.dto.CocinaDto.ComandaResponse;
import com.project.ocumare_food.cocina.dto.CocinaDto.EstadoCocina;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Lo que el personal ve de la cocina: si las comandas salieron y si la impresora está conectada. */
@RestController
@RequestMapping("/api/comandas")
public class ComandaController {

    private final CocinaService cocina;

    public ComandaController(CocinaService cocina) {
        this.cocina = cocina;
    }

    /** Comandas de un pedido (?pedidoId=) o las últimas 30. */
    @GetMapping
    public List<ComandaResponse> listar(@RequestParam(required = false) Long pedidoId) {
        return pedidoId == null ? cocina.recientes() : cocina.dePedido(pedidoId);
    }

    @GetMapping("/estado")
    public EstadoCocina estado() {
        return cocina.estado();
    }
}
