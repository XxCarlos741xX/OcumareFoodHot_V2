package com.project.ocumare_food.catalogo.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Foto de un producto subida desde la gestión de la carta.
 * Se guarda dentro de la base de datos (no en el disco del servidor): así entra en el respaldo
 * diario de mysqldump y no se pierde si se cambia de servidor. El navegador la achica antes de
 * subirla (máx. ~1000 px), por eso cada foto pesa unos 100–200 KB.
 */
@Entity
@Table(name = "imagenes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Imagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_contenido", nullable = false, length = 30)
    private String tipoContenido;

    /** Hasta ~2 MB (MySQL lo guarda como MEDIUMBLOB). */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = 2_100_000)
    private byte[] datos;

    @Column(nullable = false)
    private int tamano;

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
