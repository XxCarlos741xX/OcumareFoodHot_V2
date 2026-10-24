package com.project.ocumare_food.pedidos.repository;

import com.project.ocumare_food.pedidos.ResumenVentas.PorDiaMoneda;
import com.project.ocumare_food.pedidos.ResumenVentas.PorMeseroMoneda;
import com.project.ocumare_food.pedidos.ResumenVentas.PorMetodo;
import com.project.ocumare_food.pedidos.model.Pago;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.moneda.model.Moneda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** Consultas de recaudo: todo el dinero que entró se calcula desde los pagos, por el turno en que entró. */
public interface PagoRepository extends JpaRepository<Pago, Long> {

    @Query("""
            select new com.project.ocumare_food.pedidos.ResumenVentas$PorMetodo(
                   p.metodo, p.moneda, count(p), sum(p.monto), sum(p.equivalenteUsd))
            from Pago p where p.turno.id in :turnos
            group by p.metodo, p.moneda order by p.metodo""")
    List<PorMetodo> recaudoPorMetodo(Collection<Long> turnos);

    /** El cobro se atribuye al mesero dueño del pedido (los pedidos web quedan sin mesero). */
    @Query("""
            select new com.project.ocumare_food.pedidos.ResumenVentas$PorMeseroMoneda(
                   m.id, m.nombre, p.moneda, sum(p.monto), sum(p.equivalenteUsd))
            from Pago p join p.pedido pe left join pe.mesero m
            where p.turno.id in :turnos
            group by m.id, m.nombre, p.moneda""")
    List<PorMeseroMoneda> recaudoPorMesero(Collection<Long> turnos);

    @Query("""
            select new com.project.ocumare_food.pedidos.ResumenVentas$PorDiaMoneda(
                   t.fechaOperativa, p.moneda, sum(p.monto), sum(p.equivalenteUsd))
            from Pago p join p.turno t
            where t.fechaOperativa between :desde and :hasta
            group by t.fechaOperativa, p.moneda order by t.fechaOperativa""")
    List<PorDiaMoneda> recaudoPorDia(LocalDate desde, LocalDate hasta);
}
