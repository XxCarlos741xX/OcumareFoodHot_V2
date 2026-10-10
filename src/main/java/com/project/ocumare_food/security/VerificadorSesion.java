package com.project.ocumare_food.security;

import com.project.ocumare_food.repository.UsuarioRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * En cada petición confirma que el dueño del token sigue activo y que su sesión no fue cerrada.
 * Cuando se desactiva a alguien, se le cambia el rol o la contraseña, su "versión de sesión" sube
 * y los tokens viejos dejan de servir en la siguiente petición (antes duraban hasta 12 horas).
 */
public class VerificadorSesion implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2TokenValidatorResult CERRADA = OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", "La sesión fue cerrada; vuelve a entrar", null));

    private final UsuarioRepository usuarios;

    public VerificadorSesion(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Object uid = jwt.getClaim(SecurityConfig.CLAIM_USUARIO_ID);
        Object ver = jwt.getClaim(SecurityConfig.CLAIM_VERSION);
        if (!(uid instanceof Number id) || !(ver instanceof Number version)) return CERRADA;
        return usuarios.findById(id.longValue())
                .filter(u -> u.isActivo() && u.getVersionSesion() == version.intValue())
                .map(u -> OAuth2TokenValidatorResult.success())
                .orElse(CERRADA);
    }
}
