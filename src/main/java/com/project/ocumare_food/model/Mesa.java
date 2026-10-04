package com.project.ocumare_food.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "mesas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Mesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    /** Nombre opcional: "Terraza 1", "Barra"… */
    @Column(length = 40)
    private String nombre;

    @Builder.Default
    @Column(nullable = false)
    private int capacidad = 4;

    /** Agrupa mesas en pantalla: "Salón", "Terraza", "Barra"… Vacío = sin zona. */
    @Column(length = 40)
    private String zona;

    /** Posición para mostrarlas como están acomodadas en el local (menor = primero). */
    @Builder.Default
    @Column(nullable = false)
    private int orden = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;
}
