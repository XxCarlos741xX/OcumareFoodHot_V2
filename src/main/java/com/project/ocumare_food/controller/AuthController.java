package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.LoginRequest;
import com.project.ocumare_food.dto.LoginResponse;
import com.project.ocumare_food.dto.UsuarioResponse;
import com.project.ocumare_food.security.UsuarioActual;
import com.project.ocumare_food.service.AuthService;
import com.project.ocumare_food.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UsuarioService usuarios;

    public AuthController(AuthService auth, UsuarioService usuarios) {
        this.auth = auth;
        this.usuarios = usuarios;
    }

    /** POST /api/auth/login  { "username": "...", "password": "..." } → token */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    /** GET /api/auth/yo → datos del usuario dueño del token (el frontend lo usa al recargar). */
    @GetMapping("/yo")
    public UsuarioResponse yo(@AuthenticationPrincipal Jwt jwt) {
        return usuarios.obtener(UsuarioActual.de(jwt).id());
    }

    /** PUT /api/auth/password { "passwordActual": "...", "passwordNueva": "..." } — cambiar la clave propia. */
    @PutMapping("/password")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void cambiarPassword(@Valid @RequestBody com.project.ocumare_food.dto.PasswordRequest req,
                                @AuthenticationPrincipal Jwt jwt) {
        usuarios.cambiarPasswordPropia(req, UsuarioActual.de(jwt));
    }
}
