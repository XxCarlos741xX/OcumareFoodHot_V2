package com.project.ocumare_food.salon.config;

import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.salon.repository.MesaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SOLO en desarrollo (perfil dev): agrega 9 mesas de ejemplo para probar la app en el PC.
 * En producción esta clase no existe. Los usuarios de ejemplo están en DatosDePruebaUsuarios.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "ocumare.datos-prueba", havingValue = "true")
@Order(3)
public class DatosDePruebaSalon implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDePruebaSalon.class);

    private final MesaRepository mesas;

    public DatosDePruebaSalon(MesaRepository mesas) {
        this.mesas = mesas;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (mesas.count() > 0) return;
        for (int i = 1; i <= 9; i++) {
            mesas.save(Mesa.builder().numero(i).orden(i).capacidad(i == 9 ? 8 : 4).zona(i <= 6 ? "Salón" : "Terraza").build());
        }
        log.info("Datos de prueba cargados: 9 mesas.");
    }
}
