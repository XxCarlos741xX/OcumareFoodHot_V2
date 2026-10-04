package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.Usuario;
import com.project.ocumare_food.model.enums.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByRol(Rol rol);
    List<Usuario> findAllByOrderByNombreAsc();
}
