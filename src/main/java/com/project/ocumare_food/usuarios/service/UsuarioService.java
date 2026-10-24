package com.project.ocumare_food.usuarios.service;

import com.project.ocumare_food.usuarios.dto.CrearUsuarioRequest;
import com.project.ocumare_food.usuarios.dto.EditarUsuarioRequest;
import com.project.ocumare_food.usuarios.dto.PasswordRequest;
import com.project.ocumare_food.usuarios.dto.UsuarioResponse;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import com.project.ocumare_food.usuarios.UsuarioActual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Alta y consulta de personal. Reglas:
 *  - No hay registro público: solo ADMIN y GERENTE crean usuarios.
 *  - Un GERENTE solo puede crear MESEROS.
 *  - Un ADMIN puede crear cualquier rol.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Transactional
    public UsuarioResponse crear(CrearUsuarioRequest req, UsuarioActual actor) {
        if (!actor.esAdmin() && req.rol() != Rol.MESERO) {
            throw ErrorNegocio.prohibido("Un gerente solo puede crear meseros");
        }
        String username = req.username().trim().toLowerCase();
        if (usuarios.existsByUsername(username)) {
            throw ErrorNegocio.conflicto("Ya existe un usuario con ese nombre de usuario");
        }
        Usuario u = Usuario.builder()
                .nombre(req.nombre().trim())
                .username(username)
                .passwordHash(encoder.encode(req.password()))
                .rol(req.rol())
                .build();
        return UsuarioResponse.de(usuarios.save(u));
    }

    /**
     * Editar nombre, rol o activo. Un gerente solo puede tocar meseros y no puede ascenderlos.
     * Nadie puede desactivarse ni quitarse el rol a sí mismo (para no quedarse sin acceso).
     */
    @Transactional
    public UsuarioResponse editar(Long id, EditarUsuarioRequest req, UsuarioActual actor) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario no encontrado"));
        if (!actor.esAdmin()) {
            if (u.getRol() != Rol.MESERO) throw ErrorNegocio.prohibido("Un gerente solo puede modificar meseros");
            if (req.rol() != null && req.rol() != Rol.MESERO) throw ErrorNegocio.prohibido("Un gerente no puede cambiar el rol");
        }
        boolean esSiMismo = u.getId().equals(actor.id());
        if (esSiMismo && req.activo() != null && !req.activo()) {
            throw ErrorNegocio.invalido("No puedes desactivar tu propio usuario");
        }
        if (esSiMismo && req.rol() != null && req.rol() != u.getRol()) {
            throw ErrorNegocio.invalido("No puedes cambiar tu propio rol");
        }
        boolean cambiaRol = req.rol() != null && req.rol() != u.getRol();
        boolean seDesactiva = req.activo() != null && !req.activo() && u.isActivo();
        if (req.nombre() != null && !req.nombre().isBlank()) u.setNombre(req.nombre().trim());
        if (req.rol() != null) u.setRol(req.rol());
        if (req.activo() != null) u.setActivo(req.activo());
        if (cambiaRol || seDesactiva) u.cerrarSesiones();   // su token viejo deja de servir ya
        return UsuarioResponse.de(u);
    }

    /** Gerencia restablece la clave de otro (gerente: solo de meseros). No pide la clave anterior. */
    @Transactional
    public void restablecerPassword(Long id, PasswordRequest req, UsuarioActual actor) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario no encontrado"));
        if (!actor.esAdmin() && u.getRol() != Rol.MESERO) {
            throw ErrorNegocio.prohibido("Un gerente solo puede cambiar la clave de meseros");
        }
        u.setPasswordHash(encoder.encode(req.passwordNueva()));
        u.cerrarSesiones();
    }

    /**
     * Cualquier usuario cambia su propia clave, confirmando la actual. Cierra sus sesiones en los otros
     * equipos; devuelve el usuario actualizado para darle una sesión nueva en este.
     */
    @Transactional
    public Usuario cambiarPasswordPropia(PasswordRequest req, UsuarioActual actor) {
        Usuario u = usuarios.findById(actor.id()).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario no encontrado"));
        if (req.passwordActual() == null || !encoder.matches(req.passwordActual(), u.getPasswordHash())) {
            throw ErrorNegocio.invalido("La contraseña actual no es correcta");
        }
        u.setPasswordHash(encoder.encode(req.passwordNueva()));
        u.cerrarSesiones();
        return usuarios.saveAndFlush(u);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar() {
        return usuarios.findAllByOrderByNombreAsc().stream().map(UsuarioResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return usuarios.findById(id).map(UsuarioResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario no encontrado"));
    }
}
