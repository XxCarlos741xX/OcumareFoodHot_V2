package com.project.ocumare_food.flujo;

import com.project.ocumare_food.usuarios.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Rol cajero, bitácora de pedidos y comandas de cocina (cola de impresión, puente y CloudPRNT).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CajeroYCocinaTest extends ApiTest {

    String gerente, cajero, mesero;
    long hamburguesa, refresco, mesa1;

    @BeforeEach
    void preparar() throws Exception {
        gerente = crearYEntrar("gerente-cocina", Rol.GERENTE);
        cajero = crearYEntrar("cajero-cocina", Rol.CAJERO);
        mesero = crearYEntrar("mesero-cocina", Rol.MESERO);
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Comida\"}"));
        hamburguesa = id(post(gerente, "/api/productos", "{\"nombre\":\"Hamburguesa clásica\",\"precioUsd\":5.5,\"categoriaId\":" + cat + "}"));
        refresco = id(post(gerente, "/api/productos", "{\"nombre\":\"Piña colada\",\"precioUsd\":1.25,\"categoriaId\":" + cat + "}"));
        mesa1 = id(post(gerente, "/api/mesas", "{\"numero\":1}"));
    }

    /** GET y DELETE sin sesión (los que hace la impresora). Los de ApiTest llevan token. */
    private static MockHttpServletRequestBuilder sinSesionGet(String url) {
        return MockMvcRequestBuilders.get(url);
    }

    private static MockHttpServletRequestBuilder sinSesionDelete(String url) {
        return MockMvcRequestBuilders.delete(url);
    }

    private void abrirTurno() throws Exception {
        post(cajero, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
    }

    private long pedidoMesa() throws Exception {
        return id(post(mesero, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + mesa1 + ",\"items\":["
                + "{\"productoId\":" + hamburguesa + ",\"cantidad\":2,\"notas\":\"sin cebolla\"},"
                + "{\"productoId\":" + refresco + ",\"cantidad\":1}]}").andExpect(status().isCreated()));
    }

    private ResultActions estado(String token, long pedido, String estado) throws Exception {
        return patch(token, "/api/pedidos/" + pedido + "/estado", "{\"estado\":\"" + estado + "\"}");
    }

    private List<String> tiposComanda(long pedido) throws Exception {
        return leer(get(cajero, "/api/comandas?pedidoId=" + pedido), "$[*].tipo");
    }

    private String crearImpresora() throws Exception {
        return leer(post(gerente, "/api/impresoras", "{\"nombre\":\"Cocina\",\"columnas\":42}")
                .andExpect(status().isCreated()), "$.token");
    }

    // -------------------- Rol cajero --------------------

    @Test
    void elMeseroNoCobraNiAceptaPedidosWebYElCajeroSi() throws Exception {
        abrirTurno();
        long p = pedidoMesa();
        post(mesero, "/api/pedidos/" + p + "/pagos", "{\"metodo\":\"EFECTIVO_USD\",\"monto\":1}").andExpect(status().isForbidden());
        post(cajero, "/api/pedidos/" + p + "/pagos", "{\"metodo\":\"EFECTIVO_USD\",\"monto\":1}").andExpect(status().isOk());

        post(null, "/api/web/pedidos", "{\"claveExterna\":\"cocina-web-001\",\"clienteNombre\":\"Luis\",\"clienteTelefono\":\"04141234567\","
                + "\"entrega\":\"DELIVERY\",\"direccionEntrega\":\"El Limón\",\"pago\":\"Pago Móvil\",\"notas\":\"Tocar el timbre\","
                + "\"items\":[{\"productoId\":" + hamburguesa + ",\"cantidad\":1}]}").andExpect(status().isCreated());
        List<Number> web = leer(get(cajero, "/api/pedidos"), "$[?(@.tipo=='WEB')].id");
        long pw = web.get(0).longValue();
        estado(mesero, pw, "EN_PREPARACION").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.mensaje").value("Los pedidos de la web los acepta el cajero"));
        assertThat(tiposComanda(pw)).isEmpty();
        estado(cajero, pw, "EN_PREPARACION").andExpect(status().isOk());
        assertThat(tiposComanda(pw)).containsExactly("NUEVA");

        // El mesero solo ve en su lista lo que tomó él
        get(mesero, "/api/pedidos").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value((int) p));
        get(cajero, "/api/pedidos").andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void elCajeroAbreYCierraElTurnoPeroNoCorrigeConteosNiVeElHistorial() throws Exception {
        long turno = id(post(cajero, "/api/turnos/abrir", "{\"tipo\":\"DIA\",\"fondoInicialUsd\":10}").andExpect(status().isCreated()));
        get(cajero, "/api/turnos/" + turno + "/corte").andExpect(status().isOk());
        get(cajero, "/api/turnos").andExpect(status().isForbidden());
        get(cajero, "/api/dashboard/dia").andExpect(status().isForbidden());
        get(cajero, "/api/usuarios").andExpect(status().isForbidden());
        post(cajero, "/api/productos", "{\"nombre\":\"X\",\"precioUsd\":1,\"categoriaId\":1}").andExpect(status().isForbidden());
        post(cajero, "/api/tasas", "{\"bsPorUsd\":40}").andExpect(status().isForbidden());
        post(cajero, "/api/turnos/cerrar", "{\"conteo\":{\"efectivoUsd\":10,\"efectivoBs\":0}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteoCaja.cuadreUsd").value("CUADRA"))
                .andExpect(jsonPath("$.turno.cerradoPor").value("cajero-cocina"));
        put(cajero, "/api/turnos/" + turno + "/conteo", "{\"efectivoUsd\":5,\"efectivoBs\":0}").andExpect(status().isForbidden());
        put(gerente, "/api/turnos/" + turno + "/conteo", "{\"efectivoUsd\":9,\"efectivoBs\":0}").andExpect(status().isOk());
    }

    @Test
    void elCajeroNoAnulaYElMeseroNoAbreTurno() throws Exception {
        post(mesero, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isForbidden());
        abrirTurno();
        long p = pedidoMesa();
        post(cajero, "/api/pedidos/" + p + "/anular", "{\"motivo\":\"x\"}").andExpect(status().isForbidden());
        post(gerente, "/api/pedidos/" + p + "/anular", "{\"motivo\":\"cliente se fue\"}").andExpect(status().isOk());
    }

    @Test
    void elGerenteManejaMeserosYCajerosPeroNoGerentes() throws Exception {
        long nuevo = id(post(gerente, "/api/usuarios", "{\"nombre\":\"Caja 2\",\"username\":\"caja2\",\"password\":\"clave-segura-1\",\"rol\":\"CAJERO\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.rol").value("CAJERO")));
        post(gerente, "/api/usuarios", "{\"nombre\":\"G\",\"username\":\"gerente2\",\"password\":\"clave-segura-1\",\"rol\":\"GERENTE\"}")
                .andExpect(status().isForbidden());
        put(gerente, "/api/usuarios/" + nuevo, "{\"rol\":\"MESERO\"}").andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("MESERO"));
        put(gerente, "/api/usuarios/" + nuevo, "{\"rol\":\"GERENTE\"}").andExpect(status().isForbidden());
        put(gerente, "/api/usuarios/" + nuevo + "/password", "{\"passwordNueva\":\"otra-clave-22\"}").andExpect(status().isNoContent());
        assertThat(login("caja2", "otra-clave-22")).startsWith("Bearer ");
    }

    // -------------------- Comandas y bitácora --------------------

    @Test
    void lasComandasSiguenLoQuePasaConElPedido() throws Exception {
        abrirTurno();
        long p = pedidoMesa();
        assertThat(tiposComanda(p)).as("aún no se envía a cocina").isEmpty();

        ResultActions enviado = estado(mesero, p, "EN_PREPARACION").andExpect(status().isOk())
                .andExpect(jsonPath("$.detalles[0].enviadoCocina").value(true));
        long lineaHamb = ((Number) leer(enviado, "$.detalles[0].id")).longValue();
        assertThat(tiposComanda(p)).containsExactly("NUEVA");

        // Agregar algo con el pedido en cocina: sale enseguida como ADICIONAL
        post(mesero, "/api/pedidos/" + p + "/items", "[{\"productoId\":" + refresco + ",\"cantidad\":2}]").andExpect(status().isOk());
        assertThat(tiposComanda(p)).containsExactly("NUEVA", "ADICIONAL");

        // Lo enviado a cocina no lo quita el mesero; el cajero sí, y la cocina recibe CANCELADO
        delete(mesero, "/api/pedidos/" + p + "/items/" + lineaHamb).andExpect(status().isForbidden());
        delete(cajero, "/api/pedidos/" + p + "/items/" + lineaHamb).andExpect(status().isOk());
        assertThat(tiposComanda(p)).containsExactly("NUEVA", "ADICIONAL", "CANCELADO");

        post(mesero, "/api/pedidos/" + p + "/reimprimir", null).andExpect(status().isOk());
        post(gerente, "/api/pedidos/" + p + "/anular", "{\"motivo\":\"se fue\"}").andExpect(status().isOk());
        assertThat(tiposComanda(p)).containsExactly("NUEVA", "ADICIONAL", "CANCELADO", "REIMPRESION", "ANULADO");

        // Bitácora: el mesero no la ve; el cajero sí, con las alertas marcadas
        get(mesero, "/api/pedidos/" + p + "/bitacora").andExpect(status().isForbidden());
        get(cajero, "/api/pedidos/" + p + "/bitacora")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("CREADO"))
                .andExpect(jsonPath("$[0].usuario").value("mesero-cocina"))
                .andExpect(jsonPath("$[0].detalle").value(containsString("2 x Hamburguesa clásica")))
                .andExpect(jsonPath("$[?(@.tipo=='PRODUCTO_QUITADO')].alerta").value(true))
                .andExpect(jsonPath("$[?(@.tipo=='PRODUCTO_QUITADO')].usuario").value("cajero-cocina"))
                .andExpect(jsonPath("$[?(@.tipo=='PRODUCTO_QUITADO')].detalle").value(hasItem(containsString("después de enviarlo a cocina"))));
        List<String> alertas = leer(get(cajero, "/api/pedidos/bitacora?soloAlertas=true"), "$[*].tipo");
        assertThat(alertas).containsExactly("ANULADO", "REIMPRESION", "PRODUCTO_QUITADO");
    }

    @Test
    void loQueNoHaSalidoACocinaLoQuitaElMeseroSinComanda() throws Exception {
        abrirTurno();
        long p = pedidoMesa();
        long linea = ((Number) leer(get(mesero, "/api/pedidos/" + p), "$.detalles[1].id")).longValue();
        delete(mesero, "/api/pedidos/" + p + "/items/" + linea).andExpect(status().isOk());
        assertThat(tiposComanda(p)).isEmpty();
        post(mesero, "/api/pedidos/" + p + "/reimprimir", null).andExpect(status().isConflict());
    }

    // -------------------- Impresoras --------------------

    @Test
    void puenteDeImpresion() throws Exception {
        post(mesero, "/api/impresoras", "{\"nombre\":\"X\"}").andExpect(status().isForbidden());
        post(cajero, "/api/impresoras", "{\"nombre\":\"X\"}").andExpect(status().isForbidden());
        String token = crearImpresora();
        mvc.perform(sinSesionGet("/api/impresion/puente/token-falso/siguiente")).andExpect(status().isNotFound());
        mvc.perform(sinSesionGet("/api/impresion/puente/" + token + "/siguiente")).andExpect(status().isNoContent());

        abrirTurno();
        long p = pedidoMesa();
        estado(mesero, p, "EN_PREPARACION").andExpect(status().isOk());

        var hoja = mvc.perform(sinSesionGet("/api/impresion/puente/" + token + "/siguiente"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Comanda-Id"))
                .andExpect(content().string(containsString("##2 #1 MESA 1")))
                .andExpect(content().string(containsString("NUEVO PEDIDO")))
                .andExpect(content().string(containsString("2 x Hamburguesa clasica")))
                .andExpect(content().string(containsString("> sin cebolla")))
                .andExpect(content().string(containsString("1 x Pina colada")))
                .andExpect(content().string(containsString("Atiende: mesero-cocina")))
                .andReturn();
        String id = hoja.getResponse().getHeader("X-Comanda-Id");
        post(null, "/api/impresion/puente/" + token + "/" + id + "?ok=true", null).andExpect(status().isNoContent());
        mvc.perform(sinSesionGet("/api/impresion/puente/" + token + "/siguiente")).andExpect(status().isNoContent());
        get(cajero, "/api/comandas?pedidoId=" + p).andExpect(jsonPath("$[0].estado").value("IMPRESA"));
        get(mesero, "/api/comandas/estado")
                .andExpect(jsonPath("$.impresorasActivas").value(1))
                .andExpect(jsonPath("$.conectada").value(true))
                .andExpect(jsonPath("$.pendientes").value(0));

        // Hoja de prueba y desactivar
        post(gerente, "/api/impresoras/prueba", null).andExpect(status().isCreated());
        mvc.perform(sinSesionGet("/api/impresion/puente/" + token + "/siguiente"))
                .andExpect(content().string(containsString("Si lees esto")));
        long impresora = ((Number) leer(get(gerente, "/api/impresoras"), "$[0].id")).longValue();
        mvc.perform(sinSesionDelete("/api/impresoras/" + impresora).header("Authorization", gerente)).andExpect(status().isOk());
        mvc.perform(sinSesionGet("/api/impresion/puente/" + token + "/siguiente")).andExpect(status().isNotFound());
    }

    @Test
    void impresoraConNubeCloudPrnt() throws Exception {
        String token = crearImpresora();
        String url = "/api/impresion/cloudprnt/" + token;
        post(null, url, "{\"statusCode\":\"200%20OK\",\"printerMAC\":\"00:11:62:00:00:00\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobReady").value(false));

        abrirTurno();
        long p = id(post(mesero, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"clienteNombre\":\"Rosa\",\"items\":[{\"productoId\":"
                + hamburguesa + ",\"cantidad\":1}]}"));
        estado(mesero, p, "EN_PREPARACION").andExpect(status().isOk());

        ResultActions consulta = post(null, url, "{\"statusCode\":\"200%20OK\"}")
                .andExpect(jsonPath("$.jobReady").value(true))
                .andExpect(jsonPath("$.mediaTypes[0]").value("text/plain"));
        String trabajo = leer(consulta, "$.jobToken");
        mvc.perform(sinSesionGet(url).param("type", "text/plain").param("token", trabajo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(containsString("#1 LLEVAR")))
                .andExpect(content().string(containsString("Cliente: Rosa")))
                .andExpect(content().string(not(containsString("##"))));

        // Falla tres veces (sin papel): se deja de intentar
        for (int i = 0; i < 3; i++) {
            mvc.perform(sinSesionDelete(url).param("code", "520 Paper empty").param("token", trabajo)).andExpect(status().isOk());
        }
        post(null, url, "{}").andExpect(jsonPath("$.jobReady").value(false));
        get(cajero, "/api/comandas?pedidoId=" + p).andExpect(jsonPath("$[0].estado").value("FALLIDA"));

        // Se repone el papel y la caja reimprime
        post(cajero, "/api/pedidos/" + p + "/reimprimir", null).andExpect(status().isOk());
        String otro = leer(post(null, url, "{}").andExpect(jsonPath("$.jobReady").value(true)), "$.jobToken");
        mvc.perform(sinSesionGet(url).param("token", otro)).andExpect(content().string(containsString("REIMPRESION")));
        mvc.perform(sinSesionDelete(url).param("code", "200 OK").param("token", otro)).andExpect(status().isOk());
        post(null, url, "{}").andExpect(jsonPath("$.jobReady").value(false));
        get(gerente, "/api/impresoras")
                .andExpect(jsonPath("$[0].ultimaVia").value("CLOUDPRNT"))
                .andExpect(jsonPath("$[0].conectada").value(true));
    }
}
