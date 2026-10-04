package com.project.ocumare_food.service;

import com.project.ocumare_food.dto.LoginRequest;
import com.project.ocumare_food.dto.LoginResponse;
import com.project.ocumare_food.dto.UsuarioResponse;
import com.project.ocumare_food.exception.ErrorNegocio;
import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.repository.UsuarioRepository;
import com.project.ocumare_food.security.LimiteIntentosLogin;
import com.project.ocumare_food.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Mismo mensaje para usuario inexistente, clave errada o usuario inactivo: no se da pista. */
    static final String CREDENCIALES_INVALIDAS = "Usuario o contraseña incorrectos";

    private final AuthenticationManager authManager;
    private final UsuarioRepository usuarios;
    private final TokenService tokens;
    private final LimiteIntentosLogin limite;

    public AuthService(AuthenticationManager authManager, UsuarioRepository usuarios,
                       TokenService tokens, LimiteIntentosLogin limite) {
        this.authManager = authManager;
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.limite = limite;
    }

    public LoginResponse login(LoginRequest req) {
        String username = req.username().trim();
        if (limite.estaBloqueado(username)) {
            throw new ErrorNegocio(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera 5 minutos e intenta de nuevo.");
        }
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, req.password()));
        } catch (AuthenticationException e) {
            limite.registrarFallo(username);
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
        limite.registrarExito(username);
        Usuario u = usuarios.findByUsername(username).orElseThrow();
        TokenService.TokenEmitido t = tokens.emitir(u);
        return new LoginResponse(t.token(), t.expiraEn(), UsuarioResponse.de(u));
    }
}
