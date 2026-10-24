package com.project.ocumare_food.salon.controller;

import com.project.ocumare_food.salon.MesaService;
import com.project.ocumare_food.salon.dto.MesaDto.MesaRequest;
import com.project.ocumare_food.salon.dto.MesaDto.MesaResponse;
import com.project.ocumare_food.salon.dto.MesaDto.OrdenMesasRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Mesas. Lectura: cualquier usuario con sesión. Escritura: GERENTE y ADMIN (ver SecurityConfig).
 * DELETE desactiva (baja lógica), no borra.
 */
@RestController
@RequestMapping("/api")
public class MesaController {

    private final MesaService mesas;

    public MesaController(MesaService mesas) {
        this.mesas = mesas;
    }

    @GetMapping("/mesas")
    public List<MesaResponse> mesas(@RequestParam(defaultValue = "false") boolean incluirInactivas) {
        return mesas.listarMesas(incluirInactivas);
    }

    @PostMapping("/mesas")
    @ResponseStatus(HttpStatus.CREATED)
    public MesaResponse crearMesa(@Valid @RequestBody MesaRequest req) {
        return mesas.crearMesa(req);
    }

    @PutMapping("/mesas/{id}")
    public MesaResponse editarMesa(@PathVariable Long id, @Valid @RequestBody MesaRequest req) {
        return mesas.editarMesa(id, req);
    }

    /** Guardar el orden: { "ids": [5, 2, 7, ...] } */
    @PutMapping("/mesas/orden")
    public List<MesaResponse> ordenarMesas(@Valid @RequestBody OrdenMesasRequest req) {
        return mesas.ordenarMesas(req.ids());
    }

    /** Renumerar 1..n las mesas activas según su orden. */
    @PostMapping("/mesas/renumerar")
    public List<MesaResponse> renumerar() {
        return mesas.renumerarMesas();
    }

    @DeleteMapping("/mesas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desactivarMesa(@PathVariable Long id) {
        mesas.desactivarMesa(id);
    }
}
