package com.project.ocumare_food.pedidos.controller;

import com.project.ocumare_food.pedidos.dto.PedidoDto.*;

import static com.project.ocumare_food.pedidos.dto.PedidoDto.MAX_LINEAS;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.pedidos.service.PedidoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Pedidos del personal (MESERO, CAJERO, GERENTE, ADMIN).
 * Cobrar y ver la bitácora: CAJERO, GERENTE y ADMIN (SecurityConfig). Anular: GERENTE y ADMIN (servicio).
 */
@RestController
@RequestMapping("/api/pedidos")

public class PedidoController {

    private final PedidoService pedidos;

    public PedidoController(PedidoService pedidos) {
        this.pedidos = pedidos;
    }

    /** Lista del turno abierto (o ?turnoId=). ?soloAbiertos=true para el tablero de cocina/caja. */
    @GetMapping
    public List<PedidoResumen> listar(@RequestParam(required = false) Long turnoId,
                                      @RequestParam(defaultValue = "false") boolean soloAbiertos,
                                      @AuthenticationPrincipal Jwt jwt) {
        return pedidos.listar(turnoId, soloAbiertos, UsuarioActual.de(jwt));
    }

    /** Bitácora del turno (por defecto el abierto). ?soloAlertas=true: lo que hay que revisar. */
    @GetMapping("/bitacora")
    public List<EventoResponse> bitacoraTurno(@RequestParam(required = false) Long turnoId,
                                              @RequestParam(defaultValue = "false") boolean soloAlertas) {
        return pedidos.bitacoraTurno(turnoId, soloAlertas);
    }

    @GetMapping("/{id}/bitacora")
    public List<EventoResponse> bitacora(@PathVariable Long id) {
        return pedidos.bitacora(id);
    }

    @PostMapping("/{id}/reimprimir")
    public PedidoResponse reimprimir(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return pedidos.reimprimir(id, UsuarioActual.de(jwt));
    }

    @GetMapping("/{id}")
    public PedidoResponse obtener(@PathVariable Long id) {
        return pedidos.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest req, @AuthenticationPrincipal Jwt jwt) {
        return pedidos.crear(req, UsuarioActual.de(jwt));
    }

    @PostMapping("/{id}/items")
    public PedidoResponse agregarItems(@PathVariable Long id,
                                       @RequestBody @NotEmpty @Size(max = MAX_LINEAS) List<@Valid ItemRequest> items,
                                       @AuthenticationPrincipal Jwt jwt) {
        return pedidos.agregarItems(id, items, UsuarioActual.de(jwt));
    }

    @DeleteMapping("/{id}/items/{detalleId}")
    public PedidoResponse quitarItem(@PathVariable Long id, @PathVariable Long detalleId,
                                     @AuthenticationPrincipal Jwt jwt) {
        return pedidos.quitarItem(id, detalleId, UsuarioActual.de(jwt));
    }

    @PatchMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambiarEstadoRequest req,
                                        @AuthenticationPrincipal Jwt jwt) {
        return pedidos.cambiarEstado(id, req.estado(), UsuarioActual.de(jwt));
    }

    @PostMapping("/{id}/anular")
    public PedidoResponse anular(@PathVariable Long id, @Valid @RequestBody AnularRequest req,
                                 @AuthenticationPrincipal Jwt jwt) {
        return pedidos.anular(id, req.motivo(), UsuarioActual.de(jwt));
    }

    @PostMapping("/{id}/pagos")
    public PedidoResponse pagar(@PathVariable Long id, @Valid @RequestBody PagoRequest req,
                                @AuthenticationPrincipal Jwt jwt) {
        return pedidos.registrarPago(id, req, UsuarioActual.de(jwt));
    }
}
