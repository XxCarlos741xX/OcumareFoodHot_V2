package com.project.ocumare_food.flujo;

import com.project.ocumare_food.model.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un turno completo con números conocidos, y su corte.
 *
 * Fondo inicial: $20 y Bs 500. Tasa: 40.
 *  - Mesero A, mesa 1: 2 hamburguesas = $11.00 → paga $5 efectivo + Bs 240 pago móvil → entregado → PAGADO
 *  - Mesero B, para llevar: 2 refrescos = $2.50 → paga Bs 100 efectivo (aún no entregado)
 *  - Web: 1 hamburguesa = $5.50 → sin pagar
 *  - Mesero A, para llevar: 1 refresco → ANULADO
 *
 * Esperado: 3 pedidos válidos, 1 anulado, 2 pendientes, venta $19.00,
 *           recaudado $5 y Bs 340 (= $13.50), efectivo esperado en caja $25 y Bs 600.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CorteTurnoTest extends ApiTest {

    String gerente, meseroA, meseroB;
    long hamburguesa, refresco, mesa1, turnoId, pedidoWeb;

    @BeforeEach
    void escenario() throws Exception {
        gerente = crearYEntrar("gerente-corte", Rol.GERENTE);
        meseroA = crearYEntrar("ana-corte", Rol.MESERO);
        meseroB = crearYEntrar("beto-corte", Rol.MESERO);

        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Comida\"}"));
        hamburguesa = id(post(gerente, "/api/productos", "{\"nombre\":\"Hamburguesa\",\"precioUsd\":5.5,\"categoriaId\":" + cat + "}"));
        refresco = id(post(gerente, "/api/productos", "{\"nombre\":\"Refresco\",\"precioUsd\":1.25,\"categoriaId\":" + cat + "}"));
        mesa1 = id(post(gerente, "/api/mesas", "{\"numero\":1}"));

        turnoId = id(post(gerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\",\"fondoInicialUsd\":20,\"fondoInicialBs\":500}")
                .andExpect(status().isCreated()));
        post(gerente, "/api/tasas", "{\"bsPorUsd\":40}").andExpect(status().isCreated());

        // Mesero A, mesa 1
        long p1 = id(post(meseroA, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + mesa1
                + ",\"items\":[{\"productoId\":" + hamburguesa + ",\"cantidad\":2}]}"));
        post(meseroA, "/api/pedidos/" + p1 + "/pagos", "{\"metodo\":\"EFECTIVO_USD\",\"monto\":5}").andExpect(status().isOk());
        post(meseroA, "/api/pedidos/" + p1 + "/pagos", "{\"metodo\":\"PAGO_MOVIL\",\"monto\":240,\"referencia\":\"111\"}")
                .andExpect(status().isOk());
        for (String e : new String[]{"EN_PREPARACION", "LISTO", "ENTREGADO"}) {
            patch(meseroA, "/api/pedidos/" + p1 + "/estado", "{\"estado\":\"" + e + "\"}").andExpect(status().isOk());
        }

        // Mesero B, para llevar pagado en Bs efectivo
        long p2 = id(post(meseroB, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":2}]}"));
        post(meseroB, "/api/pedidos/" + p2 + "/pagos", "{\"metodo\":\"EFECTIVO_BS\",\"monto\":100}").andExpect(status().isOk());

        // Web sin pagar
        post(null, "/api/web/pedidos", "{\"claveExterna\":\"corte-web-001\",\"clienteNombre\":\"Luis\",\"clienteTelefono\":\"04141234567\","
                + "\"entrega\":\"RETIRO\",\"items\":[{\"productoId\":" + hamburguesa + ",\"cantidad\":1}]}").andExpect(status().isCreated());
        java.util.List<Number> idsWeb = leer(get(gerente, "/api/pedidos"), "$[?(@.tipo=='WEB')].id");
        pedidoWeb = idsWeb.get(0).longValue();

        // Anulado
        long p4 = id(post(meseroA, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}"));
        post(gerente, "/api/pedidos/" + p4 + "/anular", "{\"motivo\":\"prueba\"}").andExpect(status().isOk());
    }

    @Test
    void corteEnVivoMuestraTotalesYDejaElConteoAparte() throws Exception {
        get(gerente, "/api/turnos/" + turnoId + "/corte")
                .andExpect(status().isOk())
                // Lo que se ve de entrada
                .andExpect(jsonPath("$.totales.pedidos").value(3))
                .andExpect(jsonPath("$.totales.anulados").value(1))
                .andExpect(jsonPath("$.totales.pendientes").value(2))
                .andExpect(jsonPath("$.totales.ventaUsd").value(19.0))
                .andExpect(jsonPath("$.totales.recaudadoUsd").value(5.0))
                .andExpect(jsonPath("$.totales.recaudadoBs").value(340.0))
                .andExpect(jsonPath("$.totales.recaudadoTotalEquivUsd").value(13.5))
                .andExpect(jsonPath("$.totales.ticketPromedioUsd").value(6.33))
                // Por método: siempre los 6 en el orden fijo
                .andExpect(jsonPath("$.porMetodo.length()").value(6))
                .andExpect(jsonPath("$.porMetodo[0].metodo").value("EFECTIVO_USD"))
                .andExpect(jsonPath("$.porMetodo[0].monto").value(5.0))
                .andExpect(jsonPath("$.porMetodo[1].cantidad").value(0))
                .andExpect(jsonPath("$.porMetodo[2].monto").value(100.0))
                .andExpect(jsonPath("$.porMetodo[3].monto").value(240.0))
                // Por mesero (ordenado por venta)
                .andExpect(jsonPath("$.porMesero[0].mesero").value("ana-corte"))
                .andExpect(jsonPath("$.porMesero[0].pedidos").value(1))
                .andExpect(jsonPath("$.porMesero[0].ventaUsd").value(11.0))
                .andExpect(jsonPath("$.porMesero[0].cobradoUsd").value(5.0))
                .andExpect(jsonPath("$.porMesero[0].cobradoBs").value(240.0))
                .andExpect(jsonPath("$.porMesero[1].mesero").value("Web"))
                .andExpect(jsonPath("$.porMesero[2].mesero").value("beto-corte"))
                .andExpect(jsonPath("$.porMesero[2].cobradoBs").value(100.0))
                // El desplegable: esperado sin contar todavía
                .andExpect(jsonPath("$.conteoCaja.esperadoUsd").value(25.0))
                .andExpect(jsonPath("$.conteoCaja.esperadoBs").value(600.0))
                .andExpect(jsonPath("$.conteoCaja.cuadreUsd").value("SIN_CONTAR"));
    }

    @Test
    void cerrarConConteoDetectaFaltanteYLuegoSeCorrige() throws Exception {
        post(gerente, "/api/turnos/cerrar",
                "{\"conteo\":{\"efectivoUsd\":24,\"efectivoBs\":600},\"observaciones\":\"todo normal\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turno.estado").value("CERRADO"))
                .andExpect(jsonPath("$.turno.recaudadoUsd").value(5.0))
                .andExpect(jsonPath("$.observaciones").value("todo normal"))
                .andExpect(jsonPath("$.conteoCaja.diferenciaUsd").value(-1.0))
                .andExpect(jsonPath("$.conteoCaja.cuadreUsd").value("FALTA"))
                .andExpect(jsonPath("$.conteoCaja.cuadreBs").value("CUADRA"))
                .andExpect(jsonPath("$.conteoCaja.contadoPor").value("gerente-corte"));

        // Turno cerrado: no hay turno actual, no entran pedidos, no se cierra dos veces
        get(meseroA, "/api/turnos/actual").andExpect(status().isNotFound());
        post(meseroA, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}")
                .andExpect(status().isConflict());
        post(gerente, "/api/turnos/cerrar", null).andExpect(status().isConflict());

        // Se recontó y apareció el dólar
        put(gerente, "/api/turnos/" + turnoId + "/conteo", "{\"efectivoUsd\":25,\"efectivoBs\":600}")
                .andExpect(jsonPath("$.conteoCaja.cuadreUsd").value("CUADRA"));
    }

    @Test
    void cerrarSinConteoYContarDespues() throws Exception {
        post(gerente, "/api/turnos/cerrar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteoCaja.cuadreUsd").value("SIN_CONTAR"))
                .andExpect(jsonPath("$.totales.ventaUsd").value(19.0));
        put(gerente, "/api/turnos/" + turnoId + "/conteo", "{\"efectivoUsd\":25,\"efectivoBs\":650}")
                .andExpect(jsonPath("$.conteoCaja.diferenciaBs").value(50.0))
                .andExpect(jsonPath("$.conteoCaja.cuadreBs").value("SOBRA"));
    }

    @Test
    void elCobroCuentaEnElTurnoEnQueEntraElDinero() throws Exception {
        post(gerente, "/api/turnos/cerrar", null).andExpect(status().isOk());
        long noche = id(post(gerente, "/api/turnos/abrir", "{\"tipo\":\"NOCHE\"}").andExpect(status().isCreated()));

        // El pedido web del turno de día se cobra de noche
        post(meseroB, "/api/pedidos/" + pedidoWeb + "/pagos", "{\"metodo\":\"EFECTIVO_USD\",\"monto\":5.5}")
                .andExpect(status().isOk());

        get(gerente, "/api/turnos/" + noche + "/corte")
                .andExpect(jsonPath("$.totales.pedidos").value(0))          // la venta quedó en el turno de día
                .andExpect(jsonPath("$.totales.recaudadoUsd").value(5.5))   // pero el dinero entró de noche
                .andExpect(jsonPath("$.porMesero[0].mesero").value("Web"));
        get(gerente, "/api/turnos/" + turnoId + "/corte")
                .andExpect(jsonPath("$.totales.recaudadoUsd").value(5.0));

        // El dashboard del día suma los dos turnos
        get(gerente, "/api/dashboard/dia")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(2))
                .andExpect(jsonPath("$.totales.ventaUsd").value(19.0))
                .andExpect(jsonPath("$.totales.recaudadoUsd").value(10.5))
                .andExpect(jsonPath("$.totales.recaudadoBs").value(340.0));
    }

    @Test
    void dashboardPorRangoYPermisos() throws Exception {
        get(gerente, "/api/dashboard/rango")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias.length()").value(7))
                .andExpect(jsonPath("$.dias[6].recaudadoUsd").value(5.0))
                .andExpect(jsonPath("$.dias[6].recaudadoBs").value(340.0))
                .andExpect(jsonPath("$.totalEquivUsd").value(13.5));
        get(gerente, "/api/dashboard/rango?desde=2026-01-01&hasta=2026-12-31").andExpect(status().isBadRequest());
        get(gerente, "/api/turnos").andExpect(jsonPath("$[0].id").value((int) turnoId));

        get(meseroA, "/api/turnos/" + turnoId + "/corte").andExpect(status().isForbidden());
        get(meseroA, "/api/dashboard/dia").andExpect(status().isForbidden());
        post(meseroA, "/api/turnos/cerrar", null).andExpect(status().isForbidden());
    }
}
