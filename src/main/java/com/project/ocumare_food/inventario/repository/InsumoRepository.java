package com.project.ocumare_food.inventario.repository;

import com.project.ocumare_food.inventario.model.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    List<Insumo> findAllByOrderByActivoDescNombreAsc();

    List<Insumo> findByActivoTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /**
     * Suma (o resta, si es negativa) en una sola sentencia: dos pedidos que gastan el mismo insumo a la vez
     * no se pisan (la base bloquea la fila hasta que termina la transacción).
     * No limpia el contexto de JPA (corre dentro de la transacción del pedido); por eso el stock nunca se
     * cambia con setters: siempre con estas consultas, y se lee con {@link #stockDe(Long)}.
     */
    @Modifying(flushAutomatically = true)
    @Query("update Insumo i set i.stockActual = i.stockActual + :cantidad, i.actualizadoEn = :ahora where i.id = :id")
    int sumarStock(Long id, BigDecimal cantidad, LocalDateTime ahora);

    @Query("select i.stockActual from Insumo i where i.id = :id")
    BigDecimal stockDe(Long id);

    @Query("select i from Insumo i where i.activo = true and i.stockActual <= i.stockMinimo order by i.nombre")
    List<Insumo> bajoMinimo();
}
