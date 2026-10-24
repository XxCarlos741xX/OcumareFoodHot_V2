package com.project.ocumare_food.usuarios.config;

import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import com.project.ocumare_food.usuarios.seguridad.OcumareProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Al arrancar, si no existe ningún ADMIN, crea uno con ADMIN_USERNAME / ADMIN_PASSWORD.
 * Así nunca hace falta un registro público. Si ya hay admin, no hace nada.
 */
@Component
@org.springframework.core.annotation.Order(1)
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final OcumareProperties props;

    public AdminInicial(UsuarioRepository usuarios, PasswordEncoder encoder, OcumareProperties props) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarios.existsByRol(Rol.ADMIN)) {
            return;
        }
        String username = props.admin() == null ? null : props.admin().username();
        String password = props.admin() == null ? null : props.admin().password();
        if (username == null || username.isBlank() || password == null || password.length() < 8) {
            log.warn("No existe ningún ADMIN y no se pudo crear: define ADMIN_USERNAME y ADMIN_PASSWORD (mín. 8 caracteres).");
            return;
        }
        usuarios.save(Usuario.builder()
                .nombre("Administrador")
                .username(username.trim().toLowerCase())
                .passwordHash(encoder.encode(password))
                .rol(Rol.ADMIN)
                .build());
        log.info("Se creó el usuario ADMIN inicial '{}'. Cambia la contraseña después del primer ingreso.", username);
    }
}
