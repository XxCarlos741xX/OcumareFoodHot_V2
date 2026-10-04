package com.project.ocumare_food.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Propiedades "ocumare.*" de application.properties. */
@ConfigurationProperties(prefix = "ocumare")
public record OcumareProperties(Jwt jwt, Admin admin, Cors cors) {

    public record Jwt(String secret, int horas) {
    }

    public record Admin(String username, String password) {
    }

    public record Cors(List<String> origenes) {
    }
}
