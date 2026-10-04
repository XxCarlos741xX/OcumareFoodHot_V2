package com.project.ocumare_food.security;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno contra adivinar contraseñas: tras 5 intentos fallidos seguidos,
 * ese usuario queda bloqueado 5 minutos. Vive en memoria (se limpia al reiniciar).
 */
@Component
public class LimiteIntentosLogin {

    static final int MAX_FALLOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(5);

    private record Estado(int fallos, Instant bloqueadoHasta) {
    }

    private final ConcurrentHashMap<String, Estado> estados = new ConcurrentHashMap<>();
    private final Clock reloj;

    public LimiteIntentosLogin() {
        this(Clock.systemUTC());
    }

    LimiteIntentosLogin(Clock reloj) {
        this.reloj = reloj;
    }

    private static String clave(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean estaBloqueado(String username) {
        Estado e = estados.get(clave(username));
        return e != null && e.bloqueadoHasta() != null && reloj.instant().isBefore(e.bloqueadoHasta());
    }

    public void registrarFallo(String username) {
        estados.compute(clave(username), (k, e) -> {
            int fallos = (e == null || (e.bloqueadoHasta() != null && !reloj.instant().isBefore(e.bloqueadoHasta())))
                    ? 1 : e.fallos() + 1;
            Instant hasta = fallos >= MAX_FALLOS ? reloj.instant().plus(BLOQUEO) : null;
            return new Estado(fallos, hasta);
        });
    }

    public void registrarExito(String username) {
        estados.remove(clave(username));
    }
}
