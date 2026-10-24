package com.project.ocumare_food.usuarios.seguridad;


import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimiteIntentosLoginTest {

    /** Reloj que se puede adelantar a mano. */
    static class RelojMovible extends Clock {
        Instant ahora = Instant.parse("2026-10-03T12:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return ahora; }
    }

    @Test
    void bloqueaTrasCincoFallosYSeLiberaALosCincoMinutos() {
        RelojMovible reloj = new RelojMovible();
        LimiteIntentosLogin limite = new LimiteIntentosLogin(reloj);

        for (int i = 0; i < 4; i++) limite.registrarFallo("Ana");
        assertThat(limite.estaBloqueado("ana")).isFalse();

        limite.registrarFallo("ANA ");
        assertThat(limite.estaBloqueado("ana")).isTrue();

        reloj.ahora = reloj.ahora.plus(Duration.ofMinutes(5));
        assertThat(limite.estaBloqueado("ana")).isFalse();

        // Tras el bloqueo, el contador vuelve a empezar
        limite.registrarFallo("ana");
        assertThat(limite.estaBloqueado("ana")).isFalse();
    }

    @Test
    void unLoginCorrectoReiniciaElContador() {
        LimiteIntentosLogin limite = new LimiteIntentosLogin(new RelojMovible());
        for (int i = 0; i < 4; i++) limite.registrarFallo("luis");
        limite.registrarExito("luis");
        limite.registrarFallo("luis");
        assertThat(limite.estaBloqueado("luis")).isFalse();
    }
}
