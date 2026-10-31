package com.project.ocumare_food.compartido;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Reloj del negocio en hora de Venezuela. Todo "hoy" y "ahora" sale de aquí,
 * así el servidor puede estar en cualquier zona horaria y las pruebas pueden fijar la hora.
 *
 * Además fija la zona por defecto de la JVM en la del negocio: las fechas que pone Hibernate solo
 * (@CreationTimestamp en pedidos, pagos y bitácora) usan esa zona, y antes quedaban en la hora del
 * equipo (Colombia en el PC, UTC en un servidor) mientras turnos, tasas y comandas iban en hora de Caracas.
 */
@Configuration
public class RelojConfig {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Caracas");

    /**
     * Se llama en main(), antes de arrancar Spring: la base de datos y el driver guardan la zona por
     * defecto al iniciar, así que cambiarla después deja las fechas corridas. Las pruebas la fijan con
     * -Duser.timezone en build.gradle.
     */
    public static void usarZonaDelNegocio() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_NEGOCIO));
    }

    @Bean
    Clock reloj() {
        return Clock.system(ZONA_NEGOCIO);
    }
}
