package com.project.ocumare_food.usuarios.dto;

import java.time.Instant;

/** El token NO viaja aquí: va en la cookie HttpOnly, donde el JavaScript de la página no lo puede leer. */
public record LoginResponse(Instant expiraEn, UsuarioResponse usuario) {
}
