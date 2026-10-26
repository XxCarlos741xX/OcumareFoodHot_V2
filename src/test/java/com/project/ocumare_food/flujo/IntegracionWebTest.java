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
