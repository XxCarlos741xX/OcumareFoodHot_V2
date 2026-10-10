package com.project.ocumare_food.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * La sesión del POS viaja en una cookie que el JavaScript de la página NO puede leer:
 *  - HttpOnly: aunque alguien lograra meter código en la página, no puede robar la sesión.
 *  - Secure: solo viaja por HTTPS (en el PC de desarrollo se apaga con ocumare.sesion.cookie-segura=false).
 *  - SameSite=Strict: otra página web no puede hacer peticiones al POS usando la sesión del mesero.
 *  - Path=/api: solo se envía a la API, no al descargar imágenes o la página.
 */
@Component
public class SesionCookie {

    public static final String NOMBRE = "ocumare_sesion";
    private static final String RUTA = "/api";

    private final boolean segura;

    public SesionCookie(OcumareProperties props) {
        this.segura = props.cookieSegura();
    }

    public ResponseCookie crear(String token, Instant expiraEn) {
        long segundos = Math.max(0, Duration.between(Instant.now(), expiraEn).getSeconds());
        return base(token).maxAge(segundos).build();
    }

    public ResponseCookie borrar() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor).httpOnly(true).secure(segura).sameSite("Strict").path(RUTA);
    }

    public static String leer(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (NOMBRE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
        }
        return null;
    }
}
