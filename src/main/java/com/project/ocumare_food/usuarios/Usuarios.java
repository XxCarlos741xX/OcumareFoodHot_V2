package com.project.ocumare_food.usuarios;

import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

/**
 * API pública del módulo usuarios para los demás módulos.
 * Los otros módulos no tocan UsuarioRepository: piden aquí la referencia del usuario
 * que hizo la acción (para guardar "registrado por", "abierto por", etc.).
 */
@Service
public class Usuarios {

    private final UsuarioRepository usuarios;

    public Usuarios(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    /** Referencia liviana (no consulta la base) al usuario con ese id. */
    public Usuario referencia(Long id) {
        return usuarios.getReferenceById(id);
    }
}
