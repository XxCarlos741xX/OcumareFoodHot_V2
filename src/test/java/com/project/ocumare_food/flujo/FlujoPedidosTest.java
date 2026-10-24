package com.project.ocumare_food.flujo;

import com.project.ocumare_food.usuarios.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre la operación real de un turno por la API, con tokens de verdad.
 * Cada prueba se deshace al terminar (@Transactional), así no se pisan entre sí.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FlujoPedidosTest extends ApiTest {

    String tokenGerente;
    String tokenMesero;
    long hamburguesa;   // $5.50
    long refresco;      // $1.25
    long mesa1;

    @BeforeEach
    void preparar() throws Exception {
        tokenAdmin = login("admin", "admin123");
        tokenGerente = crearYEntrar("gerente-flujo", Rol.GERENTE);
        tokenMesero = crearYEntrar("mesero-flujo", Rol.MESERO);

        long cat = id(post(tokenGerente, "/api/categorias", "{\"nombre\":\"Hamburguesas\",\"orden\":1}")
                .andExpect(status().isCreated()));
        hamburguesa = id(post(tokenGerente, "/api/productos",
                "{\"nombre\":\"Hamburguesa clásica\",\"precioUsd\":5.5,\"categoriaId\":" + cat + "}")
                .andExpect(status().isCreated()));
        refresco = id(post(tokenGerente, "/api/productos",
                "{\"nombre\":\"Refresco\",\"precioUsd\":1.25,\"categoriaId\":" + cat + "}")
                .andExpect(status().isCreated()));
        mesa1 = id(post(tokenGerente, "/api/mesas", "{\"numero\":1}").andExpect(status().isCreated()));
    }

    private void abrirTurno() throws Exception {
        post(tokenGerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
    }

    private ResultActions pedidoMesa(long mesa, long producto, int cantidad) throws Exception {
        return post(tokenMesero, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + mesa
                + ",\"items\":[{\"productoId\":" + producto + ",\"cantidad\":" + cantidad + "}]}");
    }

    private ResultActions pagar(long pedido, String metodo, String monto, String referencia) throws Exception {
        return post(tokenMesero, "/api/pedidos/" + pedido + "/pagos", "{\"metodo\":\"" + metodo + "\",\"monto\":" + monto
                + (referencia == null ? "" : ",\"referencia\":\"" + referencia + "\"") + "}");
    }

    private void avanzarHasta(long pedido, String... estados) throws Exception {
        for (String e : estados) {
            patch(tokenMesero, "/api/pedidos/" + pedido + "/estado", "{\"estado\":\"" + e + "\"}")
                    .andExpect(status().isOk());
        }
    }

    // ------------------------------------------------------------------

    @Test
    void sinTurnoAbiertoNoSeTomanPedidos() throws Exception {
        pedidoMesa(mesa1, hamburguesa, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("No hay un turno abierto. Pide al gerente que abra el turno."));
    }

    @Test
    void sinNingunaTasaElPedidoSeCreaIgualSoloEnDolares() throws Exception {
        abrirTurno();
        pedidoMesa(mesa1, hamburguesa, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalUsd").value(11.0))
                .andExpect(jsonPath("$.totalBs").doesNotExist())
                .andExpect(jsonPath("$.mesero").value("mesero-flujo"))
                .andExpect(jsonPath("$.numeroDiario").value(1));
    }

    @Test
    void flujoCompletoConPagoMixtoYCierreAutomatico() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, hamburguesa, 2).andExpect(status().isCreated()));   // $11.00

        // Agregar un refresco a la misma cuenta → $12.25
        post(tokenMesero, "/api/pedidos/" + pedido + "/items", "[{\"productoId\":" + refresco + ",\"cantidad\":1}]")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsd").value(12.25))
                .andExpect(jsonPath("$.detalles.length()").value(2));

        // Sin tasa no se puede cobrar en Bs, pero sí en $
        pagar(pedido, "PAGO_MOVIL", "100", "1234").andExpect(status().isBadRequest());
        pagar(pedido, "EFECTIVO_USD", "5", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoPendienteUsd").value(7.25));

        // El gerente carga la tasa; el pedido la toma al cobrar en Bs
        post(tokenGerente, "/api/tasas", "{\"bsPorUsd\":40,\"fuente\":\"BCV\"}").andExpect(status().isCreated());
        pagar(pedido, "PAGO_MOVIL", "290.00", null).andExpect(status().isBadRequest());     // falta referencia
        pagar(pedido, "PAGO_MOVIL", "290.00", "0987")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasaBsPorUsd").value(40.0))
                .andExpect(jsonPath("$.totalBs").value(490.0))
                .andExpect(jsonPath("$.saldoPendienteUsd").value(0.0))
                .andExpect(jsonPath("$.estado").value("ABIERTO"));    // pagado pero aún no entregado

        // Al entregar se cierra solo
        avanzarHasta(pedido, "EN_PREPARACION", "LISTO", "ENTREGADO");
        get(tokenMesero, "/api/pedidos/" + pedido)
                .andExpect(jsonPath("$.estado").value("PAGADO"))
                .andExpect(jsonPath("$.pagos.length()").value(2))
                .andExpect(jsonPath("$.cerradoEn").exists());

        // La mesa queda libre
        post(tokenMesero, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + mesa1
                + ",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroDiario").value(2));
    }

    @Test
    void entregadoPrimeroYPagadoDespuesTambienCierra() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, refresco, 2));    // $2.50
        avanzarHasta(pedido, "EN_PREPARACION", "LISTO", "ENTREGADO");
        get(tokenMesero, "/api/pedidos/" + pedido).andExpect(jsonPath("$.estado").value("ENTREGADO"));
        pagar(pedido, "EFECTIVO_USD", "2.50", null).andExpect(jsonPath("$.estado").value("PAGADO"));
        // Ya cerrado: no admite más cambios
        pagar(pedido, "EFECTIVO_USD", "1", null).andExpect(status().isConflict());
    }

    @Test
    void reglasDeMesaEstadosYPagos() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, hamburguesa, 1));        // $5.50
        pedidoMesa(mesa1, refresco, 1).andExpect(status().isConflict());    // mesa ocupada

        get(tokenMesero, "/api/mesas").andExpect(jsonPath("$[0].pedidoAbiertoId").value((int) pedido));

        // No se saltan pasos ni se marca PAGADO a mano
        patch(tokenMesero, "/api/pedidos/" + pedido + "/estado", "{\"estado\":\"LISTO\"}").andExpect(status().isConflict());
        patch(tokenMesero, "/api/pedidos/" + pedido + "/estado", "{\"estado\":\"PAGADO\"}").andExpect(status().isBadRequest());

        // No se cobra de más
        pagar(pedido, "EFECTIVO_USD", "20", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("supera el saldo")));
    }

    @Test
    void soloLaGerenciaAnulaYSoloSinPagos() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, hamburguesa, 1));
        post(tokenMesero, "/api/pedidos/" + pedido + "/anular", "{\"motivo\":\"se fue\"}").andExpect(status().isForbidden());

        long otro = id(post(tokenMesero, "/api/pedidos", "{\"tipo\":\"PARA_LLEVAR\",\"items\":[{\"productoId\":"
                + refresco + ",\"cantidad\":1}]}"));
        pagar(otro, "EFECTIVO_USD", "1.25", null).andExpect(status().isOk());
        post(tokenGerente, "/api/pedidos/" + otro + "/anular", "{\"motivo\":\"error\"}").andExpect(status().isConflict());

        post(tokenGerente, "/api/pedidos/" + pedido + "/anular", "{\"motivo\":\"cliente se fue\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ANULADO"));
        // La mesa queda libre tras anular
        pedidoMesa(mesa1, refresco, 1).andExpect(status().isCreated());
    }

    @Test
    void losCambiosDeMenuSoloAfectanPedidosNuevos() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, hamburguesa, 1));
        long cat = ((Number) leer(get(tokenGerente, "/api/productos/" + hamburguesa), "$.categoriaId")).longValue();
        put(tokenGerente, "/api/productos/" + hamburguesa,
                "{\"nombre\":\"Hamburguesa clásica\",\"precioUsd\":7,\"categoriaId\":" + cat + "}").andExpect(status().isOk());

        get(tokenMesero, "/api/pedidos/" + pedido).andExpect(jsonPath("$.totalUsd").value(5.5));
        post(tokenMesero, "/api/pedidos/" + pedido + "/items", "[{\"productoId\":" + hamburguesa + ",\"cantidad\":1}]")
                .andExpect(jsonPath("$.totalUsd").value(12.5));

        // Agotado: no se puede pedir
        patch(tokenGerente, "/api/productos/" + refresco + "/disponible", "{\"disponible\":false}").andExpect(status().isOk());
        post(tokenMesero, "/api/pedidos/" + pedido + "/items", "[{\"productoId\":" + refresco + ",\"cantidad\":1}]")
                .andExpect(status().isConflict());
    }

    @Test
    void quitarProductosDeUnPedido() throws Exception {
        abrirTurno();
        long pedido = id(pedidoMesa(mesa1, hamburguesa, 1));
        ResultActions r = post(tokenMesero, "/api/pedidos/" + pedido + "/items",
                "[{\"productoId\":" + refresco + ",\"cantidad\":2}]");
        long detalleRefresco = ((Number) leer(r, "$.detalles[1].id")).longValue();
        long detalleHamb = ((Number) leer(r, "$.detalles[0].id")).longValue();

        delete(tokenMesero, "/api/pedidos/" + pedido + "/items/" + detalleRefresco)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsd").value(5.5));
        delete(tokenMesero, "/api/pedidos/" + pedido + "/items/" + detalleHamb).andExpect(status().isBadRequest());
    }

    @Test
    void deliveryExigeDireccionYTelefonoYSumaElEnvio() throws Exception {
        abrirTurno();
        post(tokenMesero, "/api/pedidos", "{\"tipo\":\"DELIVERY\",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}")
                .andExpect(status().isBadRequest());
        post(tokenMesero, "/api/pedidos", "{\"tipo\":\"DELIVERY\",\"clienteTelefono\":\"04241234567\","
                + "\"direccionEntrega\":\"El Limón, calle 3\",\"costoDeliveryUsd\":2,"
                + "\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalUsd").value(3.25));
    }

    @Test
    void elMeseroNoPuedeTocarElMenuNiLaTasaNiLosTurnos() throws Exception {
        post(tokenMesero, "/api/productos", "{\"nombre\":\"X\",\"precioUsd\":1,\"categoriaId\":1}").andExpect(status().isForbidden());
        post(tokenMesero, "/api/tasas", "{\"bsPorUsd\":40}").andExpect(status().isForbidden());
        post(tokenMesero, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isForbidden());
        get(tokenMesero, "/api/productos").andExpect(status().isOk());
    }

    @Test
    void soloUnTurnoAbiertoALaVez() throws Exception {
        abrirTurno();
        post(tokenGerente, "/api/turnos/abrir", "{\"tipo\":\"NOCHE\"}").andExpect(status().isConflict());
        get(tokenMesero, "/api/turnos/actual").andExpect(jsonPath("$.tipo").value("DIA"));
    }

    // -------------------- Web pública --------------------

    private String pedidoWeb(String clave, String extra) {
        return "{\"claveExterna\":\"" + clave + "\",\"clienteNombre\":\"Luis\",\"clienteTelefono\":\"04141234567\","
                + "\"entrega\":\"RETIRO\",\"items\":[{\"productoId\":" + hamburguesa + ",\"cantidad\":2}]" + extra + "}";
    }

    @Test
    void elMenuPublicoNoPideLoginYMuestraBolivaresSiHayTasa() throws Exception {
        get(null, "/api/menu").andExpect(status().isOk())
                .andExpect(jsonPath("$.tasaBsPorUsd").doesNotExist())
                .andExpect(jsonPath("$.categorias[0].productos.length()").value(2));
        post(tokenGerente, "/api/tasas", "{\"bsPorUsd\":40}");
        get(null, "/api/menu").andExpect(jsonPath("$.categorias[0].productos[0].precioBs").value(220.0));
    }

    @Test
    void pedidoWebEsIdempotenteYConsultable() throws Exception {
        post(null, "/api/web/pedidos", pedidoWeb("clave-web-0001", "")).andExpect(status().isServiceUnavailable());
        abrirTurno();

        ResultActions primero = post(null, "/api/web/pedidos", pedidoWeb("clave-web-0001", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalUsd").value(11.0));
        int numero = leer(primero, "$.numero");

        // Reintento con la misma clave: mismo pedido, no se duplica
        post(null, "/api/web/pedidos", pedidoWeb("clave-web-0001", "")).andExpect(jsonPath("$.numero").value(numero));
        get(tokenMesero, "/api/pedidos").andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("WEB"));

        get(null, "/api/web/pedidos/clave-web-0001").andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ABIERTO"));
    }

    @Test
    void pedidoWebRechazaBotsYPreciosNoVienenDelCliente() throws Exception {
        abrirTurno();
        post(null, "/api/web/pedidos", pedidoWeb("clave-bot-0001", ",\"website\":\"spam.com\""))
                .andExpect(status().isBadRequest());
        // Aunque el cliente mande un "precio", se ignora: el total sale de la base
        post(null, "/api/web/pedidos", pedidoWeb("clave-web-0002", ",\"totalUsd\":0.01"))
                .andExpect(jsonPath("$.totalUsd").value(11.0));
        // Delivery sin dirección
        post(null, "/api/web/pedidos", pedidoWeb("clave-web-0003", "").replace("RETIRO", "DELIVERY"))
                .andExpect(status().isBadRequest());
    }

    // -------------------- Usuarios --------------------

    @Test
    void gestionDeUsuariosYCambioDeClave() throws Exception {
        long meseroId = ((Number) leer(get(tokenMesero, "/api/auth/yo"), "$.id")).longValue();
        long gerenteId = ((Number) leer(get(tokenGerente, "/api/auth/yo"), "$.id")).longValue();

        // El gerente no puede ascender a un mesero ni tocar a otro gerente
        put(tokenGerente, "/api/usuarios/" + meseroId, "{\"rol\":\"GERENTE\"}").andExpect(status().isForbidden());
        put(tokenGerente, "/api/usuarios/" + gerenteId, "{\"activo\":false}").andExpect(status().isForbidden());

        // Restablece la clave del mesero y luego lo desactiva
        put(tokenGerente, "/api/usuarios/" + meseroId + "/password", "{\"passwordNueva\":\"nueva-clave-9\"}")
                .andExpect(status().isNoContent());
        login("mesero-flujo", "nueva-clave-9");
        put(tokenGerente, "/api/usuarios/" + meseroId, "{\"activo\":false}").andExpect(jsonPath("$.activo").value(false));
        post(null, "/api/auth/login", "{\"username\":\"mesero-flujo\",\"password\":\"nueva-clave-9\"}")
                .andExpect(status().isUnauthorized());

        // Cambio de clave propia exige la actual
        put(tokenGerente, "/api/auth/password", "{\"passwordActual\":\"mala\",\"passwordNueva\":\"otra-clave-1\"}")
                .andExpect(status().isBadRequest());
        put(tokenGerente, "/api/auth/password", "{\"passwordActual\":\"clave-segura-1\",\"passwordNueva\":\"otra-clave-1\"}")
                .andExpect(status().isNoContent());
        assertThat(login("gerente-flujo", "otra-clave-1")).startsWith("Bearer ");
    }
}
