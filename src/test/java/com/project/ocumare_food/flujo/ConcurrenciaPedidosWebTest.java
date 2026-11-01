package com.project.ocumare_food.flujo;

import com.jayway.jsonpath.JsonPath;
import com.project.ocumare_food.usuarios.model.Rol;
import com.project.ocumare_food.usuarios.model.Usuario;
import com.project.ocumare_food.usuarios.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Muchos pedidos web al mismo tiempo con pocas conexiones a la base (en producción el pool es de 5).
 * Antes, el aviso de Telegram pedía una segunda conexión con REQUIRES_NEW mientras el pedido tenía la suya:
 * con más pedidos simultáneos que conexiones el pool se trababa 30 s y los clientes se quedaban esperando.
 * Corre con un servidor real (puerto aleatorio) y su propia base en memoria.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrencia;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.hikari.maximum-pool-size=3",
        "spring.datasource.hikari.connection-timeout=10000"})
@DirtiesContext
class ConcurrenciaPedidosWebTest {

    private static final int PEDIDOS = 40;

    @Value("${local.server.port}") int puerto;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> enviar(String metodo, String ruta, String json, String cookie) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + ruta))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .method(metodo, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (cookie != null) b.header("Cookie", cookie);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void cuarentaPedidosWebSimultaneosConTresConexionesSalenTodosYRapido() throws Exception {
        usuarios.save(Usuario.builder().nombre("Gerente carga").username("gerente-carga")
                .passwordHash(encoder.encode("clave-segura-1")).rol(Rol.GERENTE).build());
        HttpResponse<String> login = enviar("POST", "/api/auth/login",
                "{\"username\":\"gerente-carga\",\"password\":\"clave-segura-1\"}", null);
        assertThat(login.statusCode()).isEqualTo(200);
        String cookie = login.headers().firstValue("set-cookie").orElseThrow().split(";")[0];

        long cat = ((Number) JsonPath.read(enviar("POST", "/api/categorias", "{\"nombre\":\"Carga\"}", cookie).body(), "$.id")).longValue();
        long plato = ((Number) JsonPath.read(enviar("POST", "/api/productos",
                "{\"nombre\":\"Plato carga\",\"precioUsd\":5,\"categoriaId\":" + cat + "}", cookie).body(), "$.id")).longValue();
        assertThat(enviar("POST", "/api/turnos/abrir", "{\"tipo\":\"DIA\"}", cookie).statusCode()).isEqualTo(201);

        long inicio = System.nanoTime();
        List<CompletableFuture<HttpResponse<String>>> envios = new ArrayList<>();
        for (int i = 0; i < PEDIDOS; i++) {
            String json = "{\"aceptaTerminos\":true,\"versionPoliticas\":\"2026-10-25\",\"claveExterna\":\"concurrencia-" + i + "\","
                    + "\"clienteNombre\":\"Cliente " + i + "\",\"clienteTelefono\":\"04141234567\",\"entrega\":\"RETIRO\","
                    + "\"items\":[{\"productoId\":" + plato + ",\"cantidad\":1}]}";
            envios.add(http.sendAsync(HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/web/pedidos"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString()));
        }
        Set<Integer> numeros = new HashSet<>();
        for (CompletableFuture<HttpResponse<String>> e : envios) {
            HttpResponse<String> r = e.join();
            assertThat(r.statusCode()).as(r.body()).isEqualTo(201);
            numeros.add(JsonPath.read(r.body(), "$.numero"));
        }
        Duration total = Duration.ofNanos(System.nanoTime() - inicio);

        assertThat(numeros).hasSize(PEDIDOS);                       // ninguno perdido ni con número repetido
        assertThat(total).isLessThan(Duration.ofSeconds(10));       // antes: trabado hasta el timeout del pool
        assertThat(enviar("GET", "/api/menu", null, null).statusCode()).isEqualTo(200);
    }
}
