package com.project.ocumare_food.usuarios.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Propiedades "ocumare.*" de application.properties. */
@ConfigurationProperties(prefix = "ocumare")
public record OcumareProperties(Jwt jwt, Admin admin, Cors cors, Sesion sesion, Limites limites) {

    public record Jwt(String secret, int horas) {
    }

    public record Admin(String username, String password) {
    }

    public record Cors(List<String> origenes) {
    }

    /** cookieSegura = la cookie solo viaja por HTTPS. Siempre true en producción; false solo en el PC. */
    public record Sesion(Boolean cookieSegura) {
    }

    /**
     * Máximo de peticiones por IP. Si un valor no está configurado se usa el de por defecto.
     *  - login: intentos de entrar por minuto.
     *  - pedidosWeb: pedidos enviados desde la página cada 10 minutos.
     *  - consultasWeb: consultas del estado de un pedido por minuto.
     *  - api: cualquier otra llamada a /api por minuto (todas las tablets del local suelen salir por la misma IP).
     */
    public record Limites(Integer login, Integer pedidosWeb, Integer consultasWeb, Integer api) {
    }

    public boolean cookieSegura() {
        return sesion == null || sesion.cookieSegura() == null || sesion.cookieSegura();
    }

    public int limite(java.util.function.Function<Limites, Integer> campo, int porDefecto) {
        Integer v = limites == null ? null : campo.apply(limites);
        return v == null || v <= 0 ? porDefecto : v;
    }
}
