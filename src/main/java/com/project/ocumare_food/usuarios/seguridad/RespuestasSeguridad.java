package com.project.ocumare_food.usuarios.seguridad;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Los 401 y 403 que corta Spring Security antes de llegar a los controladores salen con el mismo
 * formato JSON que el resto de errores de la API: { "status", "error", "mensaje", "campos" }.
 * Antes salían con el cuerpo vacío y el POS no tenía qué mostrar.
 */
final class RespuestasSeguridad {

    private RespuestasSeguridad() {
    }

    static AuthenticationEntryPoint sinSesion() {
        return (req, res, e) -> {
            res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            escribir(res, HttpStatus.UNAUTHORIZED, "Inicia sesión para continuar");
        };
    }

    static AccessDeniedHandler sinPermiso() {
        return (req, res, e) -> escribir(res, HttpStatus.FORBIDDEN, "No tienes permiso para esta acción");
    }

    private static void escribir(HttpServletResponse res, HttpStatus status, String mensaje) throws IOException {
        if (res.isCommitted()) return;
        res.setStatus(status.value());
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"mensaje\":\"" + mensaje + "\",\"campos\":null}");
    }
}
