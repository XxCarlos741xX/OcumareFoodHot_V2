package com.project.ocumare_food.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Freno por IP contra bots y abusos (punto 11 y 12 de la lista de seguridad):
 *  - login: evita probar contraseñas a lo bruto desde una misma IP.
 *  - pedidos web: evita que un bot llene la cocina de pedidos falsos.
 *  - consultas de estado y API en general: evita saturar el servidor.
 * Cuenta en memoria con ventanas fijas; al pasarse responde 429 con "Retry-After".
 * La IP real llega de Caddy (server.forward-headers-strategy=framework); la app solo escucha en 127.0.0.1.
 */
public class LimitePeticiones extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LimitePeticiones.class);

    record Regla(String nombre, RequestMatcher aplica, int maximo, Duration ventana, String mensaje) {
    }

    private static final class Ventana {
        final Instant inicio;
        final AtomicInteger cuenta = new AtomicInteger();

        Ventana(Instant inicio) {
            this.inicio = inicio;
        }
    }

    private final List<Regla> reglas;
    private final Clock reloj;
    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private final AtomicInteger desdeLimpieza = new AtomicInteger();

    public LimitePeticiones(OcumareProperties props) {
        this(props, Clock.systemUTC());
    }

    LimitePeticiones(OcumareProperties props, Clock reloj) {
        this.reloj = reloj;
        var rutas = PathPatternRequestMatcher.withDefaults();
        this.reglas = List.of(
                new Regla("login", rutas.matcher(HttpMethod.POST, "/api/auth/login"),
                        props.limite(OcumareProperties.Limites::login, 10), Duration.ofMinutes(1),
                        "Demasiados intentos de entrar desde esta conexión. Espera un minuto."),
                new Regla("pedido-web", rutas.matcher(HttpMethod.POST, "/api/web/pedidos"),
                        props.limite(OcumareProperties.Limites::pedidosWeb, 10), Duration.ofMinutes(10),
                        "Recibimos muchos pedidos desde tu conexión. Espera unos minutos o pide por WhatsApp."),
                new Regla("consulta-web", rutas.matcher(HttpMethod.GET, "/api/web/pedidos/*"),
                        props.limite(OcumareProperties.Limites::consultasWeb, 30), Duration.ofMinutes(1),
                        "Demasiadas consultas. Intenta de nuevo en un minuto."),
                new Regla("api", rutas.matcher("/api/**"),
                        props.limite(OcumareProperties.Limites::api, 600), Duration.ofMinutes(1),
                        "Demasiadas peticiones. Intenta de nuevo en un minuto."));
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant ahora = reloj.instant();
        String ip = request.getRemoteAddr();
        for (Regla r : reglas) {
            if (!r.aplica().matches(request)) continue;
            Ventana v = ventanas.compute(r.nombre() + "|" + ip, (k, actual) ->
                    actual == null || !ahora.isBefore(actual.inicio.plus(r.ventana())) ? new Ventana(ahora) : actual);
            if (v.cuenta.incrementAndGet() > r.maximo()) {
                long espera = Math.max(1, Duration.between(ahora, v.inicio.plus(r.ventana())).getSeconds());
                if (v.cuenta.get() == r.maximo() + 1) log.warn("Límite '{}' alcanzado por la IP {}", r.nombre(), ip);
                rechazar(response, r.mensaje(), espera);
                return;
            }
        }
        limpiarDeVezEnCuando(ahora);
        chain.doFilter(request, response);
    }

    private static void rechazar(HttpServletResponse response, String mensaje, long segundos) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(segundos));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String texto = mensaje.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"mensaje\":\"" + texto + "\"}");
    }

    /** Cada 1000 peticiones borra las ventanas vencidas para que la memoria no crezca. */
    private void limpiarDeVezEnCuando(Instant ahora) {
        if (desdeLimpieza.incrementAndGet() < 1000) return;
        desdeLimpieza.set(0);
        Duration masLarga = reglas.stream().map(Regla::ventana).max(Duration::compareTo).orElse(Duration.ofMinutes(10));
        ventanas.entrySet().removeIf(e -> !ahora.isBefore(e.getValue().inicio.plus(masLarga)));
    }
}
