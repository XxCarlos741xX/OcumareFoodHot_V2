package com.project.ocumare_food.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConsultaBcvTest {

    @Test
    void leeElPromedioDeLaRespuesta() {
        String json = "{\"moneda\":\"USD\",\"fuente\":\"oficial\",\"nombre\":\"Dólar\",\"compra\":null,\"venta\":null,"
                + "\"promedio\":866.5612,\"fechaActualizacion\":\"2026-10-02T00:00:00-04:00\"}";
        assertThat(ConsultaBcv.leerPromedio(json)).contains(new BigDecimal("866.5612"));
    }

    @Test
    void ignoraRespuestasRaras() {
        assertThat(ConsultaBcv.leerPromedio("{\"promedio\":null}")).isEmpty();
        assertThat(ConsultaBcv.leerPromedio("<html>error</html>")).isEmpty();
        assertThat(ConsultaBcv.leerPromedio("{\"promedio\":0}")).isEmpty();
        assertThat(ConsultaBcv.leerPromedio(null)).isEmpty();
    }
}
