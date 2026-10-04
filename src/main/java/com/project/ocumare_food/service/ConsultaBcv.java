package com.project.ocumare_food.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Consulta la tasa oficial del BCV en un servicio público gratuito (DolarAPI).
 * Respuesta esperada: {"fuente":"oficial","promedio":866.5612,"fechaActualizacion":"..."}.
 * Si el servicio no responde o cambia de formato, devuelve vacío y nada se rompe:
 * la tasa se puede seguir poniendo a mano.
 */
@Component
public class ConsultaBcv {

    private static final Logger log = LoggerFactory.getLogger(ConsultaBcv.class);
    private static final Pattern PROMEDIO = Pattern.compile("\"promedio\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final URI url;

    public ConsultaBcv(@Value("${ocumare.tasa-bcv.url:https://ve.dolarapi.com/v1/dolares/oficial}") String url) {
        this.url = URI.create(url);
    }

    /** Bolívares por dólar según el BCV, o vacío si no se pudo obtener. */
    public Optional<BigDecimal> tasaOficial() {
        try {
            HttpRequest req = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("Tasa BCV: el servicio respondió {}", res.statusCode());
                return Optional.empty();
            }
            return leerPromedio(res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Tasa BCV: no se pudo consultar ({})", e.toString());
            return Optional.empty();
        }
    }

    static Optional<BigDecimal> leerPromedio(String json) {
        if (json == null) return Optional.empty();
        Matcher m = PROMEDIO.matcher(json);
        if (!m.find()) return Optional.empty();
        BigDecimal valor = new BigDecimal(m.group(1));
        return valor.signum() > 0 ? Optional.of(valor) : Optional.empty();
    }
}
