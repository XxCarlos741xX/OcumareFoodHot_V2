package com.project.ocumare_food.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** passwordActual solo se exige cuando el usuario cambia su propia contraseña. */
public record PasswordRequest(String passwordActual,
                              @NotBlank @Size(min = 8, max = 72, message = "debe tener entre 8 y 72 caracteres")
                              String passwordNueva) {
}
