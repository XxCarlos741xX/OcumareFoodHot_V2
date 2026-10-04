package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.CrearUsuarioRequest;
import com.project.ocumare_food.dto.EditarUsuarioRequest;
import com.project.ocumare_food.dto.PasswordRequest;
import com.project.ocumare_food.dto.UsuarioResponse;
import com.project.ocumare_food.security.UsuarioActual;
import com.project.ocumare_food.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestión de personal. Solo GERENTE y ADMIN (ver SecurityConfig). Desactivar = PUT con activo=false. */
@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarios;

    public UsuarioController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public List<UsuarioResponse> listar() {
        return usuarios.listar();
    }

    @PutMapping("/{id}")
    public UsuarioResponse editar(@PathVariable Long id, @Valid @RequestBody EditarUsuarioRequest req,
                                  @AuthenticationPrincipal Jwt jwt) {
        return usuarios.editar(id, req, UsuarioActual.de(jwt));
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerPassword(@PathVariable Long id, @Valid @RequestBody PasswordRequest req,
                                    @AuthenticationPrincipal Jwt jwt) {
        usuarios.restablecerPassword(id, req, UsuarioActual.de(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crear(@Valid @RequestBody CrearUsuarioRequest req, @AuthenticationPrincipal Jwt jwt) {
        return usuarios.crear(req, UsuarioActual.de(jwt));
    }
}
