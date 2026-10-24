package com.project.ocumare_food.moneda.bcv;

import com.project.ocumare_food.turnos.OperacionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cada hora pregunta la tasa del BCV y la registra si el BCV publicó un valor nuevo.
 * Se apaga con TASA_BCV_ACTIVA=false (y está apagada en las pruebas).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "ocumare.tasa-bcv.activa", havingValue = "true")
public class TasaAutomatica {

    private static final Logger log = LoggerFactory.getLogger(TasaAutomatica.class);

    private final ConsultaBcv bcv;
    private final OperacionService operacion;

    public TasaAutomatica(ConsultaBcv bcv, OperacionService operacion) {
        this.bcv = bcv;
        this.operacion = operacion;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 3_600_000)
    public void revisar() {
        bcv.tasaOficial().ifPresent(valor -> {
            try {
                operacion.registrarTasaBcv(valor).ifPresent(t ->
                        log.info("Tasa BCV registrada automáticamente: {} Bs/$", t.bsPorUsd()));
            } catch (Exception e) {
                log.warn("Tasa BCV: no se registró ({})", e.getMessage());
            }
        });
    }
}
