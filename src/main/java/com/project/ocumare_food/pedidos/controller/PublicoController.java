package com.project.ocumare_food.pedidos.controller;

import com.project.ocumare_food.pedidos.dto.PedidoDto.PedidoWebRequest;
import com.project.ocumare_food.pedidos.dto.PedidoDto.PedidoWebResponse;
import com.project.ocumare_food.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Pedidos públicos (sin login) de la página web de clientes. El menú público está en catalogo (MenuPublicoController). */
@RestController
@RequestMapping("/api")
public class PublicoController {

    private final PedidoService pedidos;

    public PublicoController(PedidoService pedidos) {
        this.pedidos = pedidos;
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
