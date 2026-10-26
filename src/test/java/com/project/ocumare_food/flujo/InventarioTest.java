package com.project.ocumare_food.flujo;

import com.project.ocumare_food.usuarios.model.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Inventario: recetas, descuento al enviar a cocina, devolución al anular, agotado automático y permisos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InventarioTest extends ApiTest {

    String gerente, cajero, mesero;
    long hamburguesa, pan, carne;

    @BeforeEach
    void preparar() throws Exception {
        gerente = crearYEntrar("gerente-inv", Rol.GERENTE);
        cajero = crearYEntrar("cajero-inv", Rol.CAJERO);
        mesero = crearYEntrar("mesero-inv", Rol.MESERO);
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Inventario\"}"));
        hamburguesa = id(post(gerente, "/api/productos", "{\"nombre\":\"Burger inventario\",\"precioUsd\":6,\"categoriaId\":" + cat + "}"));
        pan = id(post(gerente, "/api/inventario/insumos",
                "{\"nombre\":\"Pan de hamburguesa\",\"unidad\":\"UNIDAD\",\"stockInicial\":10,\"stockMinimo\":3,\"costoUnitarioUsd\":0.5}")
                .andExpect(status().isCreated()));
        carne = id(post(gerente, "/api/inventario/insumos",
                "{\"nombre\":\"Carne molida\",\"unidad\":\"KG\",\"stockInicial\":2,\"stockMinimo\":0.5,\"costoUnitarioUsd\":8}"));
        put(gerente, "/api/inventario/recetas/" + hamburguesa, "{\"lineas\":[{\"insumoId\":" + pan + ",\"cantidad\":1},"
                + "{\"insumoId\":" + carne + ",\"cantidad\":0.15}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.costoUsd").value(1.70))
                .andExpect(jsonPath("$.alcanzaPara").value(10));
        post(gerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
    }

    private long pedidoWeb(String clave, int cantidad) throws Exception {
        post(null, "/api/web/pedidos", "{\"aceptaTerminos\":true,\"versionPoliticas\":\"2026-10-25\",\"claveExterna\":\"" + clave + "\","
                + "\"clienteNombre\":\"Ana\",\"clienteTelefono\":\"04141234567\",\"entrega\":\"RETIRO\","
                + "\"items\":[{\"productoId\":" + hamburguesa + ",\"cantidad\":" + cantidad + "}]}").andExpect(status().isCreated());
        List<Number> ids = leer(get(cajero, "/api/pedidos"), "$[?(@.tipo=='WEB')].id");
        return ids.stream().mapToLong(Number::longValue).max().orElseThrow();
    }

    private ResultActions aCocina(long pedido) throws Exception {
        return patch(cajero, "/api/pedidos/" + pedido + "/estado", "{\"estado\":\"EN_PREPARACION\"}").andExpect(status().isOk());
    }

    private double stock(long insumo) throws Exception {
        List<Number> s = leer(get(cajero, "/api/inventario/insumos"), "$[?(@.id==" + insumo + ")].stockActual");
        return s.get(0).doubleValue();
    }

    private boolean disponible(long producto) throws Exception {
        return leer(get(gerente, "/api/productos/" + producto), "$.disponible");
    }

    @Test
    void descuentaAlEnviarACocinaYDevuelveAlAnular() throws Exception {
        long p = pedidoWeb("inv-web-0001", 4);
        assertThat(stock(pan)).isEqualTo(10.0);             // recibido pero no aceptado: todavía no se gasta
        aCocina(p);
        assertThat(stock(pan)).isEqualTo(6.0);
        assertThat(stock(carne)).isEqualTo(1.4);
        get(cajero, "/api/inventario/movimientos?pedidoId=" + p)
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].tipo", hasItem("VENTA")))
                .andExpect(jsonPath("$[0].nota").value(org.hamcrest.Matchers.containsString("4 x Burger inventario")));

        post(gerente, "/api/pedidos/" + p + "/reimprimir", "{}").andExpect(status().isOk());
        assertThat(stock(pan)).isEqualTo(6.0);              // la copia no se prepara de nuevo

        post(gerente, "/api/pedidos/" + p + "/anular", "{\"motivo\":\"cliente canceló\"}").andExpect(status().isOk());
        assertThat(stock(pan)).isEqualTo(10.0);
        assertThat(stock(carne)).isEqualTo(2.0);
        get(cajero, "/api/inventario/movimientos?pedidoId=" + p).andExpect(jsonPath("$[*].tipo", hasItem("DEVOLUCION")));
    }

    @Test
    void agotaElPlatoCuandoFaltaUnInsumoYLoDevuelveConLaCompra() throws Exception {
        aCocina(pedidoWeb("inv-web-0002", 10));
        assertThat(stock(pan)).isEqualTo(0.0);
        assertThat(disponible(hamburguesa)).isFalse();
        get(cajero, "/api/inventario/alertas")
                .andExpect(jsonPath("$.bajoMinimo[*].nombre", hasItem("Pan de hamburguesa")))
                .andExpect(jsonPath("$.platosAgotados", hasItem("Burger inventario")));
        get(gerente, "/api/inventario/recetas/" + hamburguesa).andExpect(jsonPath("$.agotadoPorInventario").value(true))
                .andExpect(jsonPath("$.alcanzaPara").value(0));

        // El cajero registra la compra: el plato vuelve solo y el costo se actualiza
        post(cajero, "/api/inventario/movimientos", "{\"insumoId\":" + pan + ",\"tipo\":\"ENTRADA\",\"cantidad\":20,\"costoUnitarioUsd\":0.6}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockResultante").value(20));
        assertThat(disponible(hamburguesa)).isTrue();
        get(gerente, "/api/inventario/recetas/" + hamburguesa).andExpect(jsonPath("$.costoUsd").value(1.80))
                .andExpect(jsonPath("$.agotadoPorInventario").value(false));
    }

    @Test
    void siElGerenteLoAgotaAManoElInventarioNoLoPrende() throws Exception {
        patch(gerente, "/api/productos/" + hamburguesa + "/disponible", "{\"disponible\":false}").andExpect(status().isOk());
        post(cajero, "/api/inventario/movimientos", "{\"insumoId\":" + pan + ",\"tipo\":\"ENTRADA\",\"cantidad\":5}")
                .andExpect(status().isCreated());
        assertThat(disponible(hamburguesa)).isFalse();
    }

    @Test
    void mermasAjustesYPermisos() throws Exception {
        post(cajero, "/api/inventario/movimientos", "{\"insumoId\":" + carne + ",\"tipo\":\"MERMA\",\"cantidad\":0.5}")
                .andExpect(status().isBadRequest());               // sin explicar qué pasó
        post(cajero, "/api/inventario/movimientos", "{\"insumoId\":" + carne + ",\"tipo\":\"MERMA\",\"cantidad\":0.5,\"nota\":\"se dañó\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.stockResultante").value(1.5));
        post(cajero, "/api/inventario/movimientos", "{\"insumoId\":" + carne + ",\"tipo\":\"AJUSTE\",\"cantidad\":5}")
                .andExpect(status().isForbidden());
        post(gerente, "/api/inventario/movimientos", "{\"insumoId\":" + carne + ",\"tipo\":\"AJUSTE\",\"cantidad\":5}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cantidad").value(3.5))
                .andExpect(jsonPath("$.stockResultante").value(5));
        post(gerente, "/api/inventario/movimientos", "{\"insumoId\":" + carne + ",\"tipo\":\"VENTA\",\"cantidad\":1}")
                .andExpect(status().isBadRequest());

        get(mesero, "/api/inventario/insumos").andExpect(status().isForbidden());
        post(cajero, "/api/inventario/insumos", "{\"nombre\":\"Queso\",\"unidad\":\"KG\"}").andExpect(status().isForbidden());
        put(cajero, "/api/inventario/recetas/" + hamburguesa, "{\"lineas\":[]}").andExpect(status().isForbidden());
        post(gerente, "/api/inventario/insumos", "{\"nombre\":\"pan de HAMBURGUESA\",\"unidad\":\"UNIDAD\"}").andExpect(status().isConflict());
        put(gerente, "/api/inventario/recetas/" + hamburguesa, "{\"lineas\":[{\"insumoId\":" + pan + ",\"cantidad\":1},{\"insumoId\":" + pan + ",\"cantidad\":2}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void losPlatosSinRecetaNoSeTocan() throws Exception {
        put(gerente, "/api/inventario/recetas/" + hamburguesa, "{\"lineas\":[]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.alcanzaPara").value(org.hamcrest.Matchers.nullValue()));
        aCocina(pedidoWeb("inv-web-0003", 3));
        assertThat(stock(pan)).isEqualTo(10.0);
        get(gerente, "/api/inventario/recetas").andExpect(jsonPath("$[?(@.producto=='Burger inventario')].lineas.length()").value(0));
    }
}
