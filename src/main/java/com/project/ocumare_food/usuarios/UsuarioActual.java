package com.project.ocumare_food.usuarios;

import com.project.ocumare_food.usuarios.seguridad.SecurityConfig;

import com.project.ocumare_food.usuarios.model.Rol;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** Datos del usuario que viene en el token, sin ir a la base de datos. */
public record UsuarioActual(Long id, String username, Rol rol) {

    public static UsuarioActual de(Jwt jwt) {
        Number uid = jwt.getClaim(SecurityConfig.CLAIM_USUARIO_ID);
        List<String> roles = jwt.getClaimAsStringList(SecurityConfig.CLAIM_ROLES);
        return new UsuarioActual(uid == null ? null : uid.longValue(), jwt.getSubject(),
                roles == null || roles.isEmpty() ? null : Rol.valueOf(roles.get(0)));
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }

    /** Cajero, gerente o administrador. */
    public boolean esCaja() {
        return rol != null && rol.esCaja();
    }

    public boolean esGerencia() {
        return rol != null && rol.esGerencia();
    }
}
