package com.project.ocumare_food.config;

import com.project.ocumare_food.model.*;
import com.project.ocumare_food.model.enums.Rol;
import com.project.ocumare_food.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


/**
 * SOLO en desarrollo (perfil dev): agrega mesas y usuarios
 * de ejemplo para poder probar la app en el PC. En producción esta clase no existe.
 *
 * Usuarios: admin / admin123, gerente / gerente123, ana / ana12345 (mesero).
 */
@Component
@Profile("dev")
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "ocumare.datos-prueba", havingValue = "true")
@Order(3)
public class DatosDePrueba implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDePrueba.class);

    private final MesaRepository mesas;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    public DatosDePrueba(MesaRepository mesas, UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.mesas = mesas;
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mesas.count() > 0) return;
        for (int i = 1; i <= 9; i++) {
            mesas.save(Mesa.builder().numero(i).orden(i).capacidad(i == 9 ? 8 : 4).zona(i <= 6 ? "Salón" : "Terraza").build());
        }
        if (!usuarios.existsByUsername("gerente")) {
            usuarios.save(Usuario.builder().nombre("María Gerente").username("gerente")
                    .passwordHash(encoder.encode("gerente123")).rol(Rol.GERENTE).build());
        }
        if (!usuarios.existsByUsername("ana")) {
            usuarios.save(Usuario.builder().nombre("Ana Pérez").username("ana")
                    .passwordHash(encoder.encode("ana12345")).rol(Rol.MESERO).build());
        }
        log.info("Datos de prueba cargados: 9 mesas y usuarios gerente / ana.");
    }
}
