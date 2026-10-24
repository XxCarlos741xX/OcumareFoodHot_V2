package com.project.ocumare_food.catalogo.model;

import jakarta.persistence.*;
import lombok.*;

/** Categorías del menú (Hamburguesas, Perros, Bebidas…). El orden define cómo se muestran. */
@Entity
@Table(name = "categorias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String nombre;

    @Builder.Default
    @Column(nullable = false)
    private int orden = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;
}
