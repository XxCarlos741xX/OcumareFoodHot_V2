package com.project.ocumare_food.pedidos.repository;

import com.project.ocumare_food.pedidos.ResumenVentas.PorEstado;
import com.project.ocumare_food.pedidos.ResumenVentas.VentaPorMesero;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /** Estados en los que un pedido sigue "vivo" (ocupa la mesa). */
    List<EstadoPedido> ESTADOS_ABIERTOS = List.of(
            EstadoPedido.ABIERTO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);

    Optional<Pedido> findByClaveExterna(String claveExterna);

    @EntityGraph(attributePaths = {"mesa", "mesero"})
    List<Pedido> findByTurnoIdOrderByIdDesc(Long turnoId);

    @EntityGraph(attributePaths = {"mesa", "mesero"})
    List<Pedido> findByTurnoIdAndEstadoInOrderByIdDesc(Long turnoId, Collection<EstadoPedido> estados);

    /** Pedidos de una mesa que todavía no se han cerrado (para no abrir dos cuentas en la misma mesa). */
    List<Pedido> findByMesaIdAndEstadoIn(Long mesaId, Collection<EstadoPedido> estados);

    @Query("select p.mesa.id, p.id from Pedido p where p.mesa is not null and p.estado in :estados")
    List<Object[]> mesasOcupadas(Collection<EstadoPedido> estados);

    @Query("""
            select new com.project.ocumare_food.pedidos.ResumenVentas$PorEstado(p.estado, count(p), sum(p.totalUsd))
            from Pedido p where p.turno.id in :turnos group by p.estado""")
    List<PorEstado> resumenPorEstado(Collection<Long> turnos);

    @Query("""
            select new com.project.ocumare_food.pedidos.ResumenVentas$VentaPorMesero(m.id, m.nombre, count(p), sum(p.totalUsd))
            from Pedido p left join p.mesero m
            where p.turno.id in :turnos and p.estado <> com.project.ocumare_food.pedidos.model.EstadoPedido.ANULADO
            group by m.id, m.nombre""")
    List<VentaPorMesero> ventaPorMesero(Collection<Long> turnos);

    @Query("select coalesce(max(p.numeroDiario), 0) from Pedido p where p.fechaOperativa = :fecha")
    int maxNumeroDiario(LocalDate fecha);

    /** Productos más vendidos (por unidades) desde una fecha, sin contar pedidos anulados. */
    @Query("""
            select d.producto.id from Pedido p join p.detalles d
            where p.creadoEn >= :desde and p.estado <> com.project.ocumare_food.pedidos.model.EstadoPedido.ANULADO
            group by d.producto.id order by sum(d.cantidad) desc, d.producto.id asc""")
    List<Long> masVendidos(java.time.LocalDateTime desde, org.springframework.data.domain.Pageable pagina);
}
