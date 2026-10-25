package com.project.ocumare_food.cocina.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LineaComanda {

    @Column(nullable = false)
    private int cantidad;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 200)
    private String notas;
}
