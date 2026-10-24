package com.project.ocumare_food.usuarios.seguridad;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Límites por IP con valores chicos para probarlos rápido. */
@SpringBootTest(properties = {
        "ocumare.limites.login=3",
        "ocumare.limites.pedidos-web=2",
        "ocumare.limites.consultas-web=4",
        "ocumare.limites.api=50"})
@AutoConfigureMockMvc
class LimitePeticionesTest {

    @Autowired MockMvc mvc;

    private static MockHttpServletRequestBuilder desde(MockHttpServletRequestBuilder b, String ip) {
        return b.with(r -> { r.setRemoteAddr(ip); return r; });
    }

    private MockHttpServletRequestBuilder loginMalo(String ip) {
        return desde(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nadie\",\"password\":\"mala-clave\"}"), ip);
    }

    @Test
    void frenaElLoginPorIpSinAfectarAOtrasIps() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(loginMalo("192.0.2.1")).andExpect(status().isUnauthorized());
        mvc.perform(loginMalo("192.0.2.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.mensaje").exists());
        mvc.perform(loginMalo("192.0.2.2")).andExpect(status().isUnauthorized());
    }

    @Test
    void frenaLosPedidosWebEnRafaga() throws Exception {
        // Sin turno abierto responden 503, pero igual cuentan: un bot no puede martillar el endpoint.
        String pedido = "{\"claveExterna\":\"rafaga-0000001\",\"clienteNombre\":\"Bot\",\"clienteTelefono\":\"04241234567\","
                + "\"entrega\":\"RETIRO\",\"items\":[{\"productoId\":1,\"cantidad\":1}]}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(desde(post("/api/web/pedidos").contentType(MediaType.APPLICATION_JSON).content(pedido), "192.0.2.3"))
                    .andExpect(status().is(org.hamcrest.Matchers.not(429)));
        }
        mvc.perform(desde(post("/api/web/pedidos").contentType(MediaType.APPLICATION_JSON).content(pedido), "192.0.2.3"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void frenaLaConsultaDeEstadoYNoTocaLasPaginas() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(desde(get("/api/web/pedidos/no-existe-123"), "192.0.2.4")).andExpect(status().isNotFound());
        }
        mvc.perform(desde(get("/api/web/pedidos/no-existe-123"), "192.0.2.4")).andExpect(status().isTooManyRequests());
        // Las páginas (fuera de /api) no cuentan
        for (int i = 0; i < 60; i++) mvc.perform(desde(get("/index.html"), "192.0.2.4")).andExpect(status().isOk());
    }

    @Test
    void frenaLaApiEnGeneral() throws Exception {
        for (int i = 0; i < 50; i++) mvc.perform(desde(get("/api/menu"), "192.0.2.5")).andExpect(status().isOk());
        mvc.perform(desde(get("/api/menu"), "192.0.2.5")).andExpect(status().isTooManyRequests());
    }
}
