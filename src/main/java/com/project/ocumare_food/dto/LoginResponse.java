package com.project.ocumare_food.dto;

import java.time.Instant;

public record LoginResponse(String token, Instant expiraEn, UsuarioResponse usuario) {
}
