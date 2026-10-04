package com.project.ocumare_food.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj del negocio en hora de Venezuela. Todo "hoy" y "ahora" sale de aquí,
 * así el servidor puede estar en cualquier zona horaria y las pruebas pueden fijar la hora.
 */
@Configuration
public class RelojConfig {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Caracas");

    @Bean
    Clock reloj() {
        return Clock.system(ZONA_NEGOCIO);
    }
}
