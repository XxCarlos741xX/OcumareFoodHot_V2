package com.project.ocumare_food.flujo;

import com.project.ocumare_food.catalogo.config.CartaInicial;
import com.project.ocumare_food.catalogo.model.Categoria;
import com.project.ocumare_food.catalogo.model.Imagen;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.moneda.model.TasaCambio;
import com.project.ocumare_food.pedidos.model.DetallePedido;
import com.project.ocumare_food.pedidos.model.Pago;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.pedidos.model.TipoPedido;
import com.project.ocumare_food.catalogo.repository.CategoriaRepository;
import com.project.ocumare_food.catalogo.repository.ProductoRepository;
import com.project.ocumare_food.notificaciones.NotificadorPedidos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Lo que necesita la web de clientes: carta real, destacados, forma de pago y aviso al equipo. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IntegracionWebTest extends ApiTest {

    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired com.project.ocumare_food.catalogo.CatalogoService catalogo;

    private String pedidoWebDe(String clave, long producto, int cantidad) {
        return "{\"aceptaTerminos\":true,\"versionPoliticas\":\"2026-10-25\",\"claveExterna\":\"" + clave + "\",\"clienteNombre\":\"Ana\","
                + "\"clienteTelefono\":\"04141234567\",\"entrega\":\"RETIRO\",\"items\":[{\"productoId\":" + producto + ",\"cantidad\":" + cantidad + "}]}";
    }

    @Test
    void elCarruselMezclaLoImpulsadoConLoMasVendido() throws Exception {
        String gerente = crearYEntrar("gerente-carrusel", Rol.GERENTE);
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Carrusel\"}"));
        java.util.function.Function<String, Long> crear = (cuerpo) -> {
            try { return id(post(gerente, "/api/productos", cuerpo.replace("CAT", String.valueOf(cat)))); }
            catch (Exception e) { throw new RuntimeException(e); }
        };
        long impulsado = crear.apply("{\"nombre\":\"Plato que no se mueve\",\"precioUsd\":4,\"categoriaId\":CAT,\"destacado\":true}");
        long estrella = crear.apply("{\"nombre\":\"Plato estrella\",\"precioUsd\":5,\"categoriaId\":CAT}");
        long segundo = crear.apply("{\"nombre\":\"Plato segundo\",\"precioUsd\":5,\"categoriaId\":CAT}");
        long anulado = crear.apply("{\"nombre\":\"Plato anulado\",\"precioUsd\":5,\"categoriaId\":CAT}");
        long agotado = crear.apply("{\"nombre\":\"Plato agotado\",\"precioUsd\":5,\"categoriaId\":CAT}");

        post(gerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
        post(null, "/api/web/pedidos", pedidoWebDe("carrusel-0001", estrella, 40)).andExpect(status().isCreated());
        post(null, "/api/web/pedidos", pedidoWebDe("carrusel-0002", segundo, 30)).andExpect(status().isCreated());
        post(null, "/api/web/pedidos", pedidoWebDe("carrusel-0003", agotado, 35)).andExpect(status().isCreated());
        post(null, "/api/web/pedidos", pedidoWebDe("carrusel-0004", anulado, 50)).andExpect(status().isCreated());
        Number idAnular = ApiTest.<java.util.List<Number>>leer(get(gerente, "/api/pedidos"),
                "$[?(@.totalUsd == 250)].id").get(0);
        post(gerente, "/api/pedidos/" + idAnular + "/anular", "{\"motivo\":\"prueba\"}").andExpect(status().isOk());
        patch(gerente, "/api/productos/" + agotado + "/disponible", "{\"disponible\":false}").andExpect(status().isOk());

        catalogo.recalcularCarrusel();
        java.util.List<Number> ids = leer(get(null, "/api/menu"), "$.carrusel[*].id");
        java.util.List<Long> carrusel = ids.stream().map(Number::longValue).toList();
        // Primero lo que impulsa el gerente, luego los más vendidos en orden; sin anulados ni agotados.
        assertThat(carrusel).contains(impulsado, estrella, segundo).doesNotContain(anulado, agotado);
        assertThat(carrusel.indexOf(impulsado)).isLessThan(carrusel.indexOf(estrella));
        assertThat(carrusel.indexOf(estrella)).isLessThan(carrusel.indexOf(segundo));
        assertThat(carrusel).hasSizeLessThanOrEqualTo(8);
        get(null, "/api/menu")
                .andExpect(jsonPath("$.categorias[?(@.nombre=='Carrusel')].productos[?(@.nombre=='Plato que no se mueve')].motivoCarrusel").value("IMPULSADO"))
                .andExpect(jsonPath("$.categorias[?(@.nombre=='Carrusel')].productos[?(@.nombre=='Plato estrella')].motivoCarrusel").value("MAS_VENDIDO"))
                .andExpect(jsonPath("$.categorias[?(@.nombre=='Carrusel')].productos[?(@.nombre=='Plato estrella')].destacado").value(true))
                .andExpect(jsonPath("$.categorias[?(@.nombre=='Carrusel')].productos[?(@.nombre=='Plato anulado')].destacado").value(false));
    }

    @Test
    void cargaLaCartaRealUnaSolaVez() throws Exception {
        new CartaInicial(categorias, productos, true).run(null);
        long platos = productos.count();
        assertThat(categorias.count()).isEqualTo(9);
        assertThat(platos).isEqualTo(50);
        assertThat(productos.findAll()).filteredOn(Producto::isDestacado).hasSize(5);

        new CartaInicial(categorias, productos, true).run(null);   // ya hay carta: no duplica
        assertThat(productos.count()).isEqualTo(platos);

        get(null, "/api/menu").andExpect(status().isOk())
                .andExpect(jsonPath("$.categorias.length()").value(9))
                .andExpect(jsonPath("$.categorias[0].nombre").value("Hamburguesas"))
                .andExpect(jsonPath("$.categorias[0].productos[?(@.nombre=='Ocumare Gourmet')].destacado").value(true))
                .andExpect(jsonPath("$.categorias[0].productos[?(@.nombre=='Ocumare Gourmet')].imagenUrl").value("img/ocumare-gourmet.jpg"));
    }

    @Test
    void elGerenteMarcaDestacadosYLaWebEnviaFormaDePago() throws Exception {
        String gerente = crearYEntrar("gerente-web", Rol.GERENTE);
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Pruebas web\"}"));
        long prod = id(post(gerente, "/api/productos",
                "{\"nombre\":\"Combo prueba\",\"precioUsd\":5,\"categoriaId\":" + cat + ",\"destacado\":true}")
                .andExpect(jsonPath("$.destacado").value(true)));
        put(gerente, "/api/productos/" + prod, "{\"nombre\":\"Combo prueba\",\"precioUsd\":5,\"categoriaId\":" + cat + ",\"destacado\":false}")
                .andExpect(jsonPath("$.destacado").value(false));

        post(gerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
        post(null, "/api/web/pedidos", "{\"aceptaTerminos\":true,\"versionPoliticas\":\"2026-10-25\",\"claveExterna\":\"web-pago-0001\",\"clienteNombre\":\"Luis\",\"clienteTelefono\":\"0414 123 4567\","
                + "\"entrega\":\"DELIVERY\",\"direccionEntrega\":\"El Limón\",\"pago\":\"Pago Móvil\",\"notas\":\"Sin cebolla\","
                + "\"items\":[{\"productoId\":" + prod + ",\"cantidad\":2}]}").andExpect(status().isCreated());
        get(gerente, "/api/pedidos").andExpect(jsonPath("$[0].tipo").value("WEB"));
        long pedidoId = ((Number) leer(get(gerente, "/api/pedidos"), "$[0].id")).longValue();
        get(gerente, "/api/pedidos/" + pedidoId)
                .andExpect(jsonPath("$.notas").value("[Delivery] Pago: Pago Móvil. Sin cebolla"))
                .andExpect(jsonPath("$.direccionEntrega").value("El Limón"));
    }

    @Test
    void elAvisoDeTelegramTraeTodoLoQueNecesitaElEquipo() {
        Categoria c = Categoria.builder().nombre("X").build();
        Pedido p = Pedido.builder().numeroDiario(12).tipo(TipoPedido.WEB).tasaBsPorUsd(new BigDecimal("40"))
                .clienteNombre("Luis").clienteTelefono("0414 123 4567").direccionEntrega("El Limón, calle 3")
                .notas("[Delivery] Pago: Pago Móvil.").build();
        p.agregarDetalle(DetallePedido.de(Producto.builder().nombre("Ocumare Gourmet").precioUsd(new BigDecimal("8.99")).categoria(c).build(), 2, null));

        String m = NotificadorPedidos.mensaje(p);
        assertThat(m).contains("Pedido web #12", "Luis", "El Limón, calle 3", "2 x Ocumare Gourmet",
                "Total: $17.98", "Bs 719.20", "Pago Móvil", "https://wa.me/584141234567");
    }

    @Test
    void sinTokenElAvisoQuedaApagado() {
        assertThat(new NotificadorPedidos(null, "", "").activo()).isFalse();
        assertThat(new NotificadorPedidos(null, "abc", "123").activo()).isTrue();
    }
}
