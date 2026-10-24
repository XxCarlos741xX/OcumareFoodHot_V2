package com.project.ocumare_food.usuarios.service;

import com.project.ocumare_food.usuarios.dto.LoginRequest;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import com.project.ocumare_food.usuarios.seguridad.LimiteIntentosLogin;
import com.project.ocumare_food.usuarios.seguridad.TokenService;
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

    /** Resultado del login: el token va a la cookie; el usuario y el vencimiento, a la respuesta. */
    public record Sesion(TokenService.TokenEmitido token, Usuario usuario) {
    }

    /**
     * El bloqueo por intentos fallidos cuenta por usuario Y por IP: así un extraño no puede dejar
     * al administrador sin entrar desde el local escribiendo 5 claves malas desde su casa.
     */
    public Sesion login(LoginRequest req, String ip) {
        String username = req.username().trim();
        String clave = username + "@" + (ip == null ? "" : ip);
        if (limite.estaBloqueado(clave)) {
            throw new ErrorNegocio(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera 5 minutos e intenta de nuevo.");
        }
        try {
            authManager.authenticate(new UsernamePasswordAuthenticationToken(username, req.password()));
        } catch (AuthenticationException e) {
            limite.registrarFallo(clave);
            throw new ErrorNegocio(HttpStatus.UNAUTHORIZED, CREDENCIALES_INVALIDAS);
        }
        limite.registrarExito(clave);
        Usuario u = usuarios.findByUsername(username).orElseThrow();
        return new Sesion(tokens.emitir(u), u);
    }
}
