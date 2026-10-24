package com.project.ocumare_food.usuarios.dto;

import com.project.ocumare_food.usuarios.model.Rol;
import jakarta.validation.constraints.Size;

/** Todos los campos son opcionales: solo se cambia lo que venga. */
public record EditarUsuarioRequest(@Size(min = 1, max = 100) String nombre, Rol rol, Boolean activo) {
}
