package com.project.ocumare_food.cocina.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Impresora de cocina registrada por el gerente. Se identifica con un token secreto que va en la
 * dirección que se le configura (a la impresora con nube o al puente). Del token solo se guarda el hash.
 */
@Entity
@Table(name = "impresoras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Impresora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** SHA-256 en hexadecimal del token. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Caracteres por línea: 48 en papel de 80 mm, 32 en papel de 58 mm. */
    @Builder.Default
    @Column(nullable = false)
    private int columnas = 48;

    @Builder.Default
    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "ultima_conexion")
    private LocalDateTime ultimaConexion;

    /** CLOUDPRNT o PUENTE: por dónde se conectó la última vez. */
    @Column(name = "ultima_via", length = 10)
    private String ultimaVia;

    /** Lo último que reportó la impresora ("200 OK", "sin papel"…), si lo reporta. */
    @Column(name = "ultimo_estado", length = 120)
    private String ultimoEstado;
}
