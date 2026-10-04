package com.project.ocumare_food.flujo;

import com.project.ocumare_food.model.enums.Rol;
import com.project.ocumare_food.service.ConsultaBcv;
import com.project.ocumare_food.service.OperacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tasa del BCV traída por el sistema: se registra solo cuando el BCV cambia y respeta la manual. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TasaBcvTest extends ApiTest {

    @MockitoBean ConsultaBcv bcv;
    @Autowired OperacionService operacion;

    @Test
    void registraSoloCuandoElBcvCambiaYRespetaLaManual() {
        assertThat(operacion.registrarTasaBcv(new BigDecimal("866.5612"))).isPresent();
        assertThat(operacion.registrarTasaBcv(new BigDecimal("866.5612"))).isEmpty();
        assertThat(operacion.tasaVigente()).contains(new BigDecimal("866.5612"));
    }

    @Test
    void elGerentePuedeTraerlaYElMeseroNo() throws Exception {
        String gerente = crearYEntrar("gerente-bcv", Rol.GERENTE);
        String mesero = crearYEntrar("mesero-bcv", Rol.MESERO);
        when(bcv.tasaOficial()).thenReturn(Optional.of(new BigDecimal("870.10")));

        post(gerente, "/api/tasas/bcv", "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nueva").value(true))
                .andExpect(jsonPath("$.tasa.bsPorUsd").value(870.1))
                .andExpect(jsonPath("$.tasa.fuente").value("BCV automática"));
        // El gerente pone una a mano: el BCV sin cambios no la pisa
        post(gerente, "/api/tasas", "{\"bsPorUsd\":900,\"fuente\":\"Paralelo\"}").andExpect(status().isCreated());
        post(gerente, "/api/tasas/bcv", "{}").andExpect(jsonPath("$.nueva").value(false))
                .andExpect(jsonPath("$.tasa.bsPorUsd").value(900.0));

        post(mesero, "/api/tasas/bcv", "{}").andExpect(status().isForbidden());
    }

    @Test
    void siElServicioNoRespondeAvisaYNoRegistraNada() throws Exception {
        String gerente = crearYEntrar("gerente-bcv2", Rol.GERENTE);
        when(bcv.tasaOficial()).thenReturn(Optional.empty());
        post(gerente, "/api/tasas/bcv", "{}").andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("ponla a mano")));
    }

    @Test
    void rechazaUnSaltoAbsurdo() throws Exception {
        String gerente = crearYEntrar("gerente-bcv3", Rol.GERENTE);
        post(gerente, "/api/tasas", "{\"bsPorUsd\":860}").andExpect(status().isCreated());
        when(bcv.tasaOficial()).thenReturn(Optional.of(new BigDecimal("8.66")));
        post(gerente, "/api/tasas/bcv", "{}").andExpect(status().isConflict());
    }
}
