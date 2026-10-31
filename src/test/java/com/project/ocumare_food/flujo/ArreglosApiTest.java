package com.project.ocumare_food.flujo;

import com.project.ocumare_food.compartido.RelojConfig;
import com.project.ocumare_food.usuarios.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Arreglos tras probar la API a mano (oct 2026): acceso del mesero a pedidos ajenos, hora de Caracas
 * en todas las fechas, 401/403 con cuerpo JSON, listas vacías sin turno y pedidos web solo con lo que
 * se ve en la carta.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ArreglosApiTest extends ApiTest {

    String gerente, cajero, meseroA, meseroB;
    long categoria, plato, mesa1;

    @BeforeEach
    void preparar() throws Exception {
        gerente = crearYEntrar("gerente-arreglos", Rol.GERENTE);
        cajero = crearYEntrar("cajero-arreglos", Rol.CAJERO);
        meseroA = crearYEntrar("mesero-a-arreglos", Rol.MESERO);
        meseroB = crearYEntrar("mesero-b-arreglos", Rol.MESERO);
        categoria = id(post(gerente, "/api/categorias", "{\"nombre\":\"Arreglos\"}"));
        plato = id(post(gerente, "/api/productos", "{\"nombre\":\"Plato arreglos\",\"precioUsd\":5,\"categoriaId\":" + categoria + "}"));
        mesa1 = id(post(gerente, "/api/mesas", "{\"numero\":1}"));
    }

    private void abrirTurno() throws Exception {
        post(cajero, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
    }

    private String item() {
        return "[{\"productoId\":" + plato + ",\"cantidad\":1}]";
    }

    // -------------------- 1. El mesero y los pedidos ajenos --------------------

    @Test
    void elMeseroNoVeNiTocaLosPedidosParaLlevarDeOtro() throws Exception {
        abrirTurno();
        long deB = id(post(meseroB, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":" + item() + "}"));

        get(meseroA, "/api/pedidos/" + deB).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Este pedido lo atiende otra persona"));
        post(meseroA, "/api/pedidos/" + deB + "/items", item()).andExpect(status().isForbidden());
        patch(meseroA, "/api/pedidos/" + deB + "/estado", "{\"estado\":\"EN_PREPARACION\"}").andExpect(status().isForbidden());

        get(meseroB, "/api/pedidos/" + deB).andExpect(status().isOk());
        get(cajero, "/api/pedidos/" + deB).andExpect(status().isOk());
    }

    @Test
    void elMeseroPuedeCubrirUnaMesaAbiertaDeOtroPeroNoVerlaYaCerrada() throws Exception {
        abrirTurno();
        long mesa = id(post(meseroB, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + mesa1 + ",\"items\":" + item() + "}"));

        // Desde el salón, A entra a la mesa ocupada de B y le agrega un plato
        get(meseroA, "/api/pedidos/" + mesa).andExpect(status().isOk());
        post(meseroA, "/api/pedidos/" + mesa + "/items", item()).andExpect(status().isOk());

        for (String e : new String[]{"EN_PREPARACION", "LISTO", "ENTREGADO"}) {
            patch(meseroB, "/api/pedidos/" + mesa + "/estado", "{\"estado\":\"" + e + "\"}").andExpect(status().isOk());
        }
        post(cajero, "/api/pedidos/" + mesa + "/pagos", "{\"metodo\":\"EFECTIVO_USD\",\"monto\":10}")
                .andExpect(jsonPath("$.estado").value("PAGADO"));

        get(meseroA, "/api/pedidos/" + mesa).andExpect(status().isForbidden());
        get(meseroB, "/api/pedidos/" + mesa).andExpect(status().isOk());
    }

    // -------------------- 2. Hora de Caracas en todas las fechas --------------------

    @Test
    void lasFechasAutomaticasVanEnHoraDelNegocio() throws Exception {
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(RelojConfig.ZONA_NEGOCIO);
        abrirTurno();
        long p = id(post(meseroA, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":" + item() + "}"));

        LocalDateTime creado = LocalDateTime.parse(leer(get(meseroA, "/api/pedidos/" + p), "$.creadoEn"));
        LocalDateTime abierto = LocalDateTime.parse(leer(get(meseroA, "/api/turnos/actual"), "$.abiertoEn"));
        LocalDateTime ahora = LocalDateTime.now(RelojConfig.ZONA_NEGOCIO);
        assertThat(Duration.between(creado, ahora).abs()).isLessThan(Duration.ofMinutes(5));
        assertThat(Duration.between(abierto, creado).abs()).isLessThan(Duration.ofMinutes(5));
    }

    // -------------------- 3. 401 y 403 con cuerpo JSON --------------------

    @Test
    void los401Y403DeSeguridadTraenElMismoFormatoDeError() throws Exception {
        get(null, "/api/auth/yo").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.mensaje").value("Inicia sesión para continuar"));
        get("Bearer token-falso", "/api/auth/yo").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        get(meseroA, "/api/usuarios").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.mensaje").value("No tienes permiso para esta acción"));
        post(meseroA, "/api/productos", "{\"nombre\":\"X\",\"precioUsd\":1,\"categoriaId\":" + categoria + "}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.mensaje").exists());
    }

    // -------------------- 4. Sin turno, las listas salen vacías --------------------

    @Test
    void sinTurnoAbiertoLasListasDelTurnoSalenVacias() throws Exception {
        get(meseroA, "/api/pedidos").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        get(cajero, "/api/pedidos?soloAbiertos=true").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        get(cajero, "/api/pedidos/bitacora").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // El turno en sí sigue respondiendo 404 (no existe) y crear un pedido, 409
        get(meseroA, "/api/turnos/actual").andExpect(status().isNotFound());
        post(meseroA, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":" + item() + "}").andExpect(status().isConflict());
    }

    // -------------------- 5. La web solo pide lo que ve en la carta --------------------

    @Test
    void laWebNoAceptaPlatosDeUnaCategoriaOculta() throws Exception {
        abrirTurno();
        delete(gerente, "/api/categorias/" + categoria).andExpect(status().isNoContent());

        post(null, "/api/web/pedidos", "{\"aceptaTerminos\":true,\"versionPoliticas\":\"2026-10-25\",\"claveExterna\":\"oculta-0001\","
                + "\"clienteNombre\":\"Ana\",\"clienteTelefono\":\"04141234567\",\"entrega\":\"RETIRO\",\"items\":" + item() + "}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El producto " + plato + " no existe"));

        // En el local el personal todavía puede venderlo (ocultar es solo para la carta de la web)
        post(meseroA, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":" + item() + "}").andExpect(status().isCreated());
    }
}
