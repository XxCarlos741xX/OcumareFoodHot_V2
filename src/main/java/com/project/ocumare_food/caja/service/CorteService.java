package com.project.ocumare_food.caja.service;

import com.project.ocumare_food.caja.dto.CorteDto.*;
import com.project.ocumare_food.turnos.dto.TurnoDto.TurnoResponse;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import com.project.ocumare_food.turnos.model.EstadoTurno;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.moneda.model.Moneda;
import com.project.ocumare_food.pedidos.ConsultasPedidos;
import com.project.ocumare_food.pedidos.ResumenVentas;
import com.project.ocumare_food.turnos.TurnoService;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.usuarios.Usuarios;
import com.project.ocumare_food.compartido.Dinero;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Cortes de turno y dashboard.
 *
 * Reglas:
 *  - El recaudo sale SIEMPRE de los pagos y se cuenta en el turno en que entró el dinero.
 *  - La venta (valor de pedidos) se cuenta en el turno en que se creó el pedido.
 *  - El conteo de caja compara solo efectivo: fondo inicial + cobros en efectivo vs lo contado.
 *  - Cerrar un turno no exige que no haya pedidos abiertos (el local es 24h: una mesa puede seguir
 *    abierta en el turno siguiente). Se informan como "pendientes".
 */
@Service
public class CorteService {

    private final TurnoService turnos;
    private final ConsultasPedidos ventas;
    private final Usuarios usuarios;
    private final Clock reloj;

    public CorteService(TurnoService turnos, ConsultasPedidos ventas, Usuarios usuarios, Clock reloj) {
        this.turnos = turnos;
        this.ventas = ventas;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    private static BigDecimal cero() {
        return Dinero.monto(BigDecimal.ZERO);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? cero() : Dinero.monto(v);
    }

    // ===================== Cierre y conteo =====================

    @Transactional
    public CorteTurno cerrarTurno(CerrarTurnoRequest req, UsuarioActual actor) {
        Turno t = turnos.bloquearTurnoAbierto()
                .orElseThrow(() -> ErrorNegocio.conflicto("No hay ningún turno abierto para cerrar"));
        List<Long> ids = List.of(t.getId());
        Totales tot = totales(ids);
        Map<MetodoPago, LineaMetodo> metodos = porMetodoMapa(ids);

        t.setEstado(EstadoTurno.CERRADO);
        t.setCerradoEn(LocalDateTime.now(reloj));
        t.setCerradoPor(usuarios.referencia(actor.id()));
        t.setCantidadPedidos((int) tot.pedidos());
        t.setTotalVentaUsd(tot.ventaUsd());
        t.setRecaudadoUsd(tot.recaudadoUsd());
        t.setRecaudadoBs(tot.recaudadoBs());
        t.setEfectivoUsdEsperado(n(t.getFondoInicialUsd()).add(montoMetodo(metodos, MetodoPago.EFECTIVO_USD)));
        t.setEfectivoBsEsperado(n(t.getFondoInicialBs()).add(montoMetodo(metodos, MetodoPago.EFECTIVO_BS)));
        if (req != null && req.observaciones() != null && !req.observaciones().isBlank()) {
            t.setObservaciones(req.observaciones().trim());
        }
        if (req != null && req.conteo() != null) {
            aplicarConteo(t, req.conteo(), actor);
        }
        turnos.guardarCambios();
        return corte(t);
    }

    /** Registrar o corregir el conteo de caja de un turno ya cerrado. */
    @Transactional
    public CorteTurno registrarConteo(Long turnoId, ConteoRequest req, UsuarioActual actor) {
        Turno t = turno(turnoId);
        if (t.getEstado() != EstadoTurno.CERRADO) {
            throw ErrorNegocio.conflicto("El conteo se registra al cerrar el turno o después de cerrarlo");
        }
        aplicarConteo(t, req, actor);
        turnos.guardarCambios();
        return corte(t);
    }

    private void aplicarConteo(Turno t, ConteoRequest c, UsuarioActual actor) {
        t.setEfectivoUsdContado(Dinero.monto(c.efectivoUsd()));
        t.setEfectivoBsContado(Dinero.monto(c.efectivoBs()));
        t.setConteoPor(usuarios.referencia(actor.id()));
        t.setConteoEn(LocalDateTime.now(reloj));
    }

    // ===================== Consultas =====================

    /** Corte de un turno. Si está abierto, es una vista previa "en vivo". */
    @Transactional(readOnly = true)
    public CorteTurno corte(Long turnoId) {
        return corte(turno(turnoId));
    }

    @Transactional(readOnly = true)
    public List<TurnoResponse> historial(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta == null ? LocalDate.now(reloj) : hasta;
        LocalDate d = desde == null ? h.minusDays(30) : desde;
        validarRango(d, h);
        return turnos.entre(d, h)
                .stream().map(TurnoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public DashboardDia dashboardDia(LocalDate fecha) {
        LocalDate f = fecha == null ? LocalDate.now(reloj) : fecha;
        List<Turno> delDia = turnos.delDia(f);
        List<Long> ids = delDia.stream().map(Turno::getId).toList();
        if (ids.isEmpty()) {
            Totales vacio = new Totales(0, 0, 0, cero(), cero(), cero(), cero(), cero());
            return new DashboardDia(f, vacio, List.of(), List.of(), List.of());
        }
        List<TurnoDelDia> porTurno = delDia.stream()
                .map(t -> new TurnoDelDia(t.getId(), t.getTipo().name(), t.getEstado().name(), totales(List.of(t.getId()))))
                .toList();
        return new DashboardDia(f, totales(ids), porTurno, List.copyOf(porMetodoMapa(ids).values()), porMesero(ids));
    }

    /** Serie diaria de recaudo para la gráfica (máximo 93 días). */
    @Transactional(readOnly = true)
    public DashboardRango dashboardRango(LocalDate desde, LocalDate hasta) {
        LocalDate h = hasta == null ? LocalDate.now(reloj) : hasta;
        LocalDate d = desde == null ? h.minusDays(6) : desde;
        validarRango(d, h);
        Map<LocalDate, BigDecimal[]> porDia = new TreeMap<>();
        for (LocalDate x = d; !x.isAfter(h); x = x.plusDays(1)) {
            porDia.put(x, new BigDecimal[]{cero(), cero(), cero()});
        }
        for (ResumenVentas.PorDiaMoneda fila : ventas.recaudoPorDia(d, h)) {
            BigDecimal[] v = porDia.get(fila.fecha());
            if (fila.moneda() == Moneda.USD) v[0] = v[0].add(n(fila.monto()));
            else v[1] = v[1].add(n(fila.monto()));
            v[2] = v[2].add(n(fila.equivalenteUsd()));
        }
        List<PuntoSerie> dias = porDia.entrySet().stream()
                .map(e -> new PuntoSerie(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2])).toList();
        return new DashboardRango(d, h,
                dias.stream().map(PuntoSerie::recaudadoUsd).reduce(cero(), BigDecimal::add),
                dias.stream().map(PuntoSerie::recaudadoBs).reduce(cero(), BigDecimal::add),
                dias.stream().map(PuntoSerie::totalEquivUsd).reduce(cero(), BigDecimal::add),
                dias);
    }

    private static void validarRango(LocalDate d, LocalDate h) {
        if (d.isAfter(h)) throw ErrorNegocio.invalido("La fecha 'desde' es posterior a 'hasta'");
        if (ChronoUnit.DAYS.between(d, h) > 92) throw ErrorNegocio.invalido("El rango máximo es de 93 días");
    }

    // ===================== Armado del corte =====================

    private Turno turno(Long id) {
        return turnos.buscar(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Turno no encontrado"));
    }

    private CorteTurno corte(Turno t) {
        List<Long> ids = List.of(t.getId());
        Map<MetodoPago, LineaMetodo> metodos = porMetodoMapa(ids);
        return new CorteTurno(TurnoResponse.de(t), t.getObservaciones(), totales(ids),
                List.copyOf(metodos.values()), porMesero(ids), conteoCaja(t, metodos));
    }

    private Totales totales(List<Long> ids) {
        long validos = 0, anulados = 0, pendientes = 0;
        BigDecimal venta = cero();
        for (ResumenVentas.PorEstado e : ventas.resumenPorEstado(ids)) {
            if (e.estado() == EstadoPedido.ANULADO) {
                anulados += e.cantidad();
                continue;
            }
            validos += e.cantidad();
            venta = venta.add(n(e.totalUsd()));
            if (e.estado() != EstadoPedido.PAGADO) pendientes += e.cantidad();
        }
        BigDecimal usd = cero(), bs = cero(), equiv = cero();
        for (ResumenVentas.PorMetodo m : ventas.recaudoPorMetodo(ids)) {
            if (m.moneda() == Moneda.USD) usd = usd.add(n(m.monto()));
            else bs = bs.add(n(m.monto()));
            equiv = equiv.add(n(m.equivalenteUsd()));
        }
        BigDecimal ticket = validos == 0 ? cero()
                : venta.divide(BigDecimal.valueOf(validos), 2, RoundingMode.HALF_UP);
        return new Totales(validos, anulados, pendientes, venta, usd, bs, equiv, ticket);
    }

    /** Siempre devuelve todos los métodos (en 0 los que no se usaron), en el orden del enum. */
    private Map<MetodoPago, LineaMetodo> porMetodoMapa(List<Long> ids) {
        Map<MetodoPago, LineaMetodo> mapa = new EnumMap<>(MetodoPago.class);
        for (MetodoPago m : MetodoPago.values()) {
            mapa.put(m, new LineaMetodo(m, m.getMoneda(), 0, cero(), cero()));
        }
        for (ResumenVentas.PorMetodo f : ventas.recaudoPorMetodo(ids)) {
            mapa.put(f.metodo(), new LineaMetodo(f.metodo(), f.moneda(), f.cantidad(), n(f.monto()), n(f.equivalenteUsd())));
        }
        return mapa;
    }

    private static BigDecimal montoMetodo(Map<MetodoPago, LineaMetodo> mapa, MetodoPago m) {
        return mapa.get(m).monto();
    }

    /** Venta por mesero (por pedidos tomados) + lo cobrado de sus pedidos en el período. "Web" agrupa los pedidos de la página. */
    private List<LineaMesero> porMesero(List<Long> ids) {
        record Acum(Long id, String nombre, long[] pedidos, BigDecimal[] v) {
        }
        Map<Long, Acum> mapa = new LinkedHashMap<>();
        final Long WEB = -1L;
        for (ResumenVentas.VentaPorMesero f : ventas.ventaPorMesero(ids)) {
            Long key = f.meseroId() == null ? WEB : f.meseroId();
            Acum a = mapa.computeIfAbsent(key, k -> new Acum(f.meseroId(), f.meseroId() == null ? "Web" : f.mesero(),
                    new long[]{0}, new BigDecimal[]{cero(), cero(), cero(), cero()}));
            a.pedidos()[0] += f.pedidos();
            a.v()[0] = a.v()[0].add(n(f.ventaUsd()));
        }
        for (ResumenVentas.PorMeseroMoneda f : ventas.recaudoPorMesero(ids)) {
            Long key = f.meseroId() == null ? WEB : f.meseroId();
            Acum a = mapa.computeIfAbsent(key, k -> new Acum(f.meseroId(), f.meseroId() == null ? "Web" : f.mesero(),
                    new long[]{0}, new BigDecimal[]{cero(), cero(), cero(), cero()}));
            if (f.moneda() == Moneda.USD) a.v()[1] = a.v()[1].add(n(f.monto()));
            else a.v()[2] = a.v()[2].add(n(f.monto()));
            a.v()[3] = a.v()[3].add(n(f.equivalenteUsd()));
        }
        return mapa.values().stream()
                .map(a -> new LineaMesero(a.id(), a.nombre(), a.pedidos()[0], a.v()[0], a.v()[1], a.v()[2], a.v()[3]))
                .sorted(Comparator.comparing(LineaMesero::ventaUsd).reversed())
                .toList();
    }

    private ConteoCaja conteoCaja(Turno t, Map<MetodoPago, LineaMetodo> metodos) {
        BigDecimal cobradoUsd = montoMetodo(metodos, MetodoPago.EFECTIVO_USD);
        BigDecimal cobradoBs = montoMetodo(metodos, MetodoPago.EFECTIVO_BS);
        BigDecimal esperadoUsd = n(t.getFondoInicialUsd()).add(cobradoUsd);
        BigDecimal esperadoBs = n(t.getFondoInicialBs()).add(cobradoBs);
        BigDecimal contUsd = t.getEfectivoUsdContado();
        BigDecimal contBs = t.getEfectivoBsContado();
        BigDecimal difUsd = contUsd == null ? null : contUsd.subtract(esperadoUsd);
        BigDecimal difBs = contBs == null ? null : contBs.subtract(esperadoBs);
        return new ConteoCaja(n(t.getFondoInicialUsd()), n(t.getFondoInicialBs()), cobradoUsd, cobradoBs,
                esperadoUsd, esperadoBs, contUsd, contBs, difUsd, difBs, cuadre(difUsd), cuadre(difBs),
                t.getConteoPor() == null ? null : t.getConteoPor().getNombre(), t.getConteoEn());
    }

    private static Cuadre cuadre(BigDecimal diferencia) {
        if (diferencia == null) return Cuadre.SIN_CONTAR;
        int s = diferencia.signum();
        return s == 0 ? Cuadre.CUADRA : s > 0 ? Cuadre.SOBRA : Cuadre.FALTA;
    }
}
