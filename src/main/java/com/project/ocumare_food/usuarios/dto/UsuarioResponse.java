package com.project.ocumare_food.usuarios.dto;

import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.model.Rol;

/** Lo que se muestra de un usuario. Nunca incluye el hash de la contraseña. */
public record UsuarioResponse(Long id, String nombre, String username, Rol rol, boolean activo) {

    public static UsuarioResponse de(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getUsername(), u.getRol(), u.isActivo());
    }
}
