package com.project.ocumare_food.usuarios.config;

import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOLO en desarrollo (perfil dev): usuarios de ejemplo para probar la app en el PC.
 * En producción esta clase no existe.
 *
 * Usuarios: admin / admin123, gerente / gerente123, ana / ana12345 (mesero).
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "ocumare.datos-prueba", havingValue = "true")
@Order(3)
public class DatosDePruebaUsuarios implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDePruebaUsuarios.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public DatosDePruebaUsuarios(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean cargo = false;
        if (!usuarios.existsByUsername("gerente")) {
            usuarios.save(Usuario.builder().nombre("María Gerente").username("gerente")
                    .passwordHash(encoder.encode("gerente123")).rol(Rol.GERENTE).build());
            cargo = true;
        }
        if (!usuarios.existsByUsername("ana")) {
            usuarios.save(Usuario.builder().nombre("Ana Pérez").username("ana")
                    .passwordHash(encoder.encode("ana12345")).rol(Rol.MESERO).build());
            cargo = true;
        }
        if (cargo) log.info("Datos de prueba cargados: usuarios gerente / ana.");
    }
}
