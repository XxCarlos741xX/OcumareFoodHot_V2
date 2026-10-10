package com.project.ocumare_food.model;

import com.project.ocumare_food.model.enums.Rol;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Personal del restaurante: meseros, gerentes y administradores. */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /** Hash BCrypt. Nunca se guarda la contraseña en texto plano. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    /** Baja lógica: un mesero que se va no se borra, para no perder su historial de pedidos. */
    @Builder.Default
    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Sube cada vez que hay que sacar a la persona de todas partes (se desactiva, cambia de rol o de
     * contraseña). Los tokens llevan este número; si ya no coincide, el token deja de servir.
     */
    @Builder.Default
    @Column(name = "version_sesion", nullable = false)
    private int versionSesion = 0;

    public void cerrarSesiones() {
        versionSesion++;
    }

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;
}
