package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.CatalogoDto.MenuResponse;
import com.project.ocumare_food.dto.PedidoDto.PedidoWebRequest;
import com.project.ocumare_food.dto.PedidoDto.PedidoWebResponse;
import com.project.ocumare_food.service.CatalogoService;
import com.project.ocumare_food.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Endpoints públicos (sin login) que usa la página web de clientes. */
@RestController
@RequestMapping("/api")
public class PublicoController {

    private final CatalogoService catalogo;
    private final PedidoService pedidos;

    public PublicoController(CatalogoService catalogo, PedidoService pedidos) {
        this.catalogo = catalogo;
        this.pedidos = pedidos;
    }

    @GetMapping("/menu")
    public MenuResponse menu() {
        return catalogo.menuPublico();
    }

    @PostMapping("/web/pedidos")
    public ResponseEntity<PedidoWebResponse> crearPedidoWeb(@Valid @RequestBody PedidoWebRequest req) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(pedidos.crearWeb(req));
        } catch (DataIntegrityViolationException e) {
            // Dos envíos idénticos al mismo tiempo: el segundo choca con la clave única → devolver el primero.
            return ResponseEntity.ok(pedidos.estadoWeb(req.claveExterna()));
        }
    }

    /** El cliente consulta cómo va su pedido con la misma clave que generó su navegador. */
    @GetMapping("/web/pedidos/{claveExterna}")
    public PedidoWebResponse estado(@PathVariable String claveExterna) {
        return pedidos.estadoWeb(claveExterna);
    }
}
