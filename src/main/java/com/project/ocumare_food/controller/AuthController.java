package com.project.ocumare_food.controller;

import com.project.ocumare_food.dto.LoginRequest;
import com.project.ocumare_food.dto.LoginResponse;
import com.project.ocumare_food.dto.PasswordRequest;
import com.project.ocumare_food.dto.UsuarioResponse;
import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.security.SesionCookie;
import com.project.ocumare_food.security.TokenService;
import com.project.ocumare_food.security.UsuarioActual;
import com.project.ocumare_food.service.AuthService;
import com.project.ocumare_food.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UsuarioService usuarios;
    private final TokenService tokens;
    private final SesionCookie cookie;

    public AuthController(AuthService auth, UsuarioService usuarios, TokenService tokens, SesionCookie cookie) {
        this.auth = auth;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.cookie = cookie;
    }

    /** POST /api/auth/login  { "username": "...", "password": "..." } → cookie de sesión + datos del usuario */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        AuthService.Sesion s = auth.login(req, http.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.crear(s.token().token(), s.token().expiraEn()).toString())
                .body(new LoginResponse(s.token().expiraEn(), UsuarioResponse.de(s.usuario())));
    }

    /** POST /api/auth/salir → borra la cookie de sesión de este equipo. */
    @PostMapping("/salir")
    public ResponseEntity<Void> salir() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.borrar().toString()).build();
    }

    /** GET /api/auth/yo → datos del usuario dueño de la sesión (el frontend lo usa al recargar). */
    @GetMapping("/yo")
    public UsuarioResponse yo(@AuthenticationPrincipal Jwt jwt) {
        return usuarios.obtener(UsuarioActual.de(jwt).id());
    }

    /**
     * PUT /api/auth/password { "passwordActual": "...", "passwordNueva": "..." } — cambiar la clave propia.
     * Cierra la sesión en los demás equipos y entrega una sesión nueva en este.
     */
    @PutMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody PasswordRequest req, @AuthenticationPrincipal Jwt jwt) {
        Usuario u = usuarios.cambiarPasswordPropia(req, UsuarioActual.de(jwt));
        TokenService.TokenEmitido t = tokens.emitir(u);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.crear(t.token(), t.expiraEn()).toString()).build();
    }
}
