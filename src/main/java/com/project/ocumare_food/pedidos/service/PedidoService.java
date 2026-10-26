package com.project.ocumare_food.pedidos.service;


import com.project.ocumare_food.pedidos.dto.PedidoDto.*;

import static com.project.ocumare_food.pedidos.dto.PedidoDto.MAX_LINEAS;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.pedidos.model.DetallePedido;
import com.project.ocumare_food.pedidos.model.Pago;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.turnos.model.Turno;
import com.project.ocumare_food.moneda.model.Moneda;
import com.project.ocumare_food.pedidos.model.EstadoPedido;
import com.project.ocumare_food.pedidos.model.MetodoPago;
import com.project.ocumare_food.pedidos.model.TipoPedido;
import com.project.ocumare_food.pedidos.repository.PedidoRepository;
import com.project.ocumare_food.catalogo.CatalogoService;
import com.project.ocumare_food.moneda.TasaService;
import com.project.ocumare_food.pedidos.ComandaSolicitada;
import com.project.ocumare_food.pedidos.ComandaSolicitada.Linea;
import com.project.ocumare_food.pedidos.ComandaSolicitada.TipoComanda;
import com.project.ocumare_food.pedidos.PedidoWebRecibido;
import com.project.ocumare_food.pedidos.model.EventoPedido;
import com.project.ocumare_food.pedidos.model.TipoEvento;
import com.project.ocumare_food.pedidos.repository.EventoPedidoRepository;
import com.project.ocumare_food.salon.MesaService;
import com.project.ocumare_food.turnos.TurnoService;
import com.project.ocumare_food.usuarios.UsuarioActual;
import com.project.ocumare_food.usuarios.Usuarios;
import com.project.ocumare_food.compartido.Dinero;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Flujo completo de un pedido:
 *
 *   ABIERTO → EN_PREPARACION → LISTO → ENTREGADO → PAGADO (cerrado)
 *                     cualquiera (sin pagos) → ANULADO (solo gerencia)
 *
 * Quién puede qué (además de SecurityConfig):
 * - Mesero: toma pedidos, agrega platos, los envía a cocina y quita los que aún no salieron a cocina.
 *   No cobra, no acepta pedidos web y solo ve en la lista los pedidos que tomó él.
 * - Cajero: todo lo anterior, cobra, acepta pedidos web, quita platos ya enviados a cocina y ve todos los pedidos.
 * - Gerente y admin: además anulan.
 *
 * Cada acción queda en la bitácora (EventoPedido) y lo que la cocina debe saber sale como
 * evento ComandaSolicitada (el módulo cocina lo convierte en una comanda impresa).
 *
 * - El pedido se asocia al mesero del token y al turno abierto.
 * - Los precios SIEMPRE se toman de la base de datos, nunca del cliente.
 * - La tasa no es obligatoria (ver TasaService).
 * - Un pedido pasa a PAGADO solo cuando está ENTREGADO y su saldo es 0, sin importar el orden.
 */
@Service
public class PedidoService {

    /** Margen por redondeo al convertir Bs → USD (medio centavo por cada lado no alcanza con pagos mixtos). */
    static final BigDecimal TOLERANCIA_USD = new BigDecimal("0.05");

    private static final Set<EstadoPedido> EDITABLES = EnumSet.of(
            EstadoPedido.ABIERTO, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO, EstadoPedido.ENTREGADO);

    private static final Map<EstadoPedido, EstadoPedido> SIGUIENTE = Map.of(
            EstadoPedido.ABIERTO, EstadoPedido.EN_PREPARACION,
            EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO,
            EstadoPedido.LISTO, EstadoPedido.ENTREGADO);

    private final PedidoRepository pedidos;
    private final EventoPedidoRepository bitacora;
    private final CatalogoService catalogo;
    private final MesaService mesas;
    private final TurnoService turnos;
    private final Usuarios usuarios;
    private final TasaService tasas;
    private final Clock reloj;
    private final ApplicationEventPublisher eventos;

    public PedidoService(PedidoRepository pedidos, EventoPedidoRepository bitacora, CatalogoService catalogo,
                         MesaService mesas, TurnoService turnos, Usuarios usuarios, TasaService tasas, Clock reloj,
                         ApplicationEventPublisher eventos) {
        this.pedidos = pedidos;
        this.bitacora = bitacora;
        this.catalogo = catalogo;
        this.mesas = mesas;
        this.turnos = turnos;
        this.usuarios = usuarios;
        this.tasas = tasas;
        this.reloj = reloj;
        this.eventos = eventos;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ===================== Crear =====================

    @Transactional
    public PedidoResponse crear(CrearPedidoRequest req, UsuarioActual actor) {
        if (req.tipo() == TipoPedido.WEB) {
            throw ErrorNegocio.invalido("Los pedidos WEB solo entran por la página");
        }
        Mesa mesa = null;
        if (req.tipo() == TipoPedido.MESA) {
            if (req.mesaId() == null) throw ErrorNegocio.invalido("Indica la mesa");
            mesa = mesas.buscar(req.mesaId()).orElseThrow(() -> ErrorNegocio.noEncontrado("Mesa no encontrada"));
            if (!mesa.isActiva()) throw ErrorNegocio.invalido("La mesa está desactivada");
        } else if (req.mesaId() != null) {
            throw ErrorNegocio.invalido("Solo los pedidos tipo MESA llevan mesa");
        }
        if (req.tipo() == TipoPedido.DELIVERY
                && (limpio(req.direccionEntrega()) == null || limpio(req.clienteTelefono()) == null)) {
            throw ErrorNegocio.invalido("Un delivery necesita dirección y teléfono del cliente");
        }

        Turno turno = turnoAbiertoBloqueado("No hay un turno abierto. Pide al cajero o al gerente que abra el turno.");
        if (mesa != null && !pedidos.findByMesaIdAndEstadoIn(mesa.getId(), PedidoRepository.ESTADOS_ABIERTOS).isEmpty()) {
            throw ErrorNegocio.conflicto("La mesa " + mesa.getNumero() + " ya tiene un pedido abierto; agrégale productos a ese");
        }

        Pedido p = nuevoPedido(turno, req.tipo());
        p.setMesa(mesa);
        p.setMesero(usuarios.referencia(actor.id()));
        p.setClienteNombre(limpio(req.clienteNombre()));
        p.setClienteTelefono(limpio(req.clienteTelefono()));
        p.setDireccionEntrega(req.tipo() == TipoPedido.DELIVERY ? limpio(req.direccionEntrega()) : null);
        p.setCostoDeliveryUsd(req.tipo() == TipoPedido.DELIVERY && req.costoDeliveryUsd() != null
                ? Dinero.monto(req.costoDeliveryUsd()) : BigDecimal.ZERO);
        p.setNotas(limpio(req.notas()));
        List<DetallePedido> lineas = agregarItems(p, req.items());
        Pedido guardado = pedidos.save(p);
        anotar(guardado, TipoEvento.CREADO, "Tomó el pedido: " + resumen(lineas), actor, false);
        return PedidoResponse.de(guardado);
    }

    /** Pedido desde la página web. Idempotente: la misma claveExterna devuelve el mismo pedido. */
    @Transactional
    public PedidoWebResponse crearWeb(PedidoWebRequest req) {
        if (req.website() != null && !req.website().isBlank()) {
            throw ErrorNegocio.invalido("Pedido rechazado");          // bot que llenó el campo trampa
        }
        if (req.entrega() == EntregaWeb.DELIVERY && limpio(req.direccionEntrega()) == null) {
            throw ErrorNegocio.invalido("Indica la dirección de entrega");
        }
        Optional<Pedido> previo = pedidos.findByClaveExterna(req.claveExterna());
        if (previo.isPresent()) return PedidoWebResponse.de(previo.get());

        Turno turno = turnoAbiertoBloqueado(null);
        // Revisar de nuevo con el turno bloqueado: un reintento simultáneo pudo entrar justo antes.
        previo = pedidos.findByClaveExterna(req.claveExterna());
        if (previo.isPresent()) return PedidoWebResponse.de(previo.get());

        Pedido p = nuevoPedido(turno, TipoPedido.WEB);
        p.setClaveExterna(req.claveExterna());
        p.setTerminosAceptadosEn(LocalDateTime.now(reloj));
        p.setVersionPoliticas(limpio(req.versionPoliticas()) == null ? "sin versión" : limpio(req.versionPoliticas()));
        p.setClienteNombre(limpio(req.clienteNombre()));
        p.setClienteTelefono(limpio(req.clienteTelefono()));
        p.setDireccionEntrega(req.entrega() == EntregaWeb.DELIVERY ? limpio(req.direccionEntrega()) : null);
        StringBuilder notas = new StringBuilder(req.entrega() == EntregaWeb.RETIRO ? "[Retira en el local]" : "[Delivery]");
        if (limpio(req.pago()) != null) notas.append(" Pago: ").append(limpio(req.pago())).append('.');
        if (limpio(req.notas()) != null) notas.append(' ').append(limpio(req.notas()));
        p.setNotas(notas.length() > 500 ? notas.substring(0, 500) : notas.toString());
        List<DetallePedido> lineas = agregarItems(p, req.items());
        Pedido guardado = pedidos.save(p);
        anotar(guardado, TipoEvento.CREADO, "Pedido recibido por la web (aceptó términos y privacidad, versión "
                + guardado.getVersionPoliticas() + "): " + resumen(lineas), null, false);
        eventos.publishEvent(new PedidoWebRecibido(guardado.getId()));
        return PedidoWebResponse.de(guardado);
    }

    @Transactional(readOnly = true)
    public PedidoWebResponse estadoWeb(String claveExterna) {
        return pedidos.findByClaveExterna(claveExterna).map(PedidoWebResponse::de)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Pedido no encontrado"));
    }

    private Turno turnoAbiertoBloqueado(String mensajeSiNoHay) {
        return turnos.bloquearTurnoAbierto().orElseThrow(() -> mensajeSiNoHay == null
                ? new ErrorNegocio(HttpStatus.SERVICE_UNAVAILABLE, "El local no está recibiendo pedidos en este momento")
                : ErrorNegocio.conflicto(mensajeSiNoHay));
    }

    /** Número diario = máximo del día + 1. Es seguro porque el turno abierto está bloqueado. */
    private Pedido nuevoPedido(Turno turno, TipoPedido tipo) {
        return Pedido.builder()
                .turno(turno)
                .fechaOperativa(turno.getFechaOperativa())
                .numeroDiario(pedidos.maxNumeroDiario(turno.getFechaOperativa()) + 1)
                .tipo(tipo)
                .tasaBsPorUsd(tasas.tasaVigente().orElse(null))
                .build();
    }

    /** Agrega las líneas al pedido y devuelve las que creó. */
    private List<DetallePedido> agregarItems(Pedido p, List<ItemRequest> items) {
        if (p.getDetalles().size() + items.size() > MAX_LINEAS) {
            throw ErrorNegocio.invalido("El pedido no puede tener más de " + MAX_LINEAS + " líneas");
        }
        List<DetallePedido> nuevas = new ArrayList<>();
        Map<Long, Producto> porId = new HashMap<>();
        catalogo.productosPorId(items.stream().map(ItemRequest::productoId).toList())
                .forEach(pr -> porId.put(pr.getId(), pr));
        for (ItemRequest it : items) {
            Producto pr = porId.get(it.productoId());
            if (pr == null || !pr.isActivo()) {
                throw ErrorNegocio.invalido("El producto " + it.productoId() + " no existe");
            }
            if (!pr.isDisponible()) {
                throw ErrorNegocio.conflicto("\"" + pr.getNombre() + "\" está agotado");
            }
            DetallePedido d = DetallePedido.de(pr, it.cantidad(), limpio(it.notas()));
            p.agregarDetalle(d);
            nuevas.add(d);
        }
        return nuevas;
    }

    // ===================== Consultar =====================

    /**
     * Pedidos del turno abierto (o de un turno dado). soloAbiertos = los que siguen en curso.
     * El mesero solo ve los que tomó él; cajero y gerencia ven todos (web incluidos).
     */
    @Transactional(readOnly = true)
    public List<PedidoResumen> listar(Long turnoId, boolean soloAbiertos, UsuarioActual actor) {
        Long id = turnoId != null ? turnoId : turnos.turnoAbierto()
                .map(Turno::getId)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No hay ningún turno abierto"));
        List<Pedido> lista = soloAbiertos
                ? pedidos.findByTurnoIdAndEstadoInOrderByIdDesc(id, PedidoRepository.ESTADOS_ABIERTOS)
                : pedidos.findByTurnoIdOrderByIdDesc(id);
        return lista.stream()
                .filter(p -> actor.esCaja() || (p.getMesero() != null && p.getMesero().getId().equals(actor.id())))
                .map(PedidoResumen::de).toList();
    }

    /** Bitácora de un pedido, de la más vieja a la más nueva. */
    @Transactional(readOnly = true)
    public List<EventoResponse> bitacora(Long pedidoId) {
        pedido(pedidoId);
        return bitacora.findByPedidoIdOrderByIdAsc(pedidoId).stream().map(EventoResponse::de).toList();
    }

    /** Bitácora de un turno (por defecto el abierto), de lo más nuevo a lo más viejo. */
    @Transactional(readOnly = true)
    public List<EventoResponse> bitacoraTurno(Long turnoId, boolean soloAlertas) {
        Long id = turnoId != null ? turnoId : turnos.turnoAbierto()
                .map(Turno::getId)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("No hay ningún turno abierto"));
        List<EventoPedido> lista = soloAlertas
                ? bitacora.findByTurnoIdAndAlertaTrueOrderByIdDesc(id)
                : bitacora.findByTurnoIdOrderByIdDesc(id);
        return lista.stream().map(EventoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id) {
        return PedidoResponse.de(pedido(id));
    }

    private Pedido pedido(Long id) {
        return pedidos.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Pedido no encontrado"));
    }

    private Pedido pedidoEditable(Long id) {
        Pedido p = pedido(id);
        if (!EDITABLES.contains(p.getEstado())) {
            throw ErrorNegocio.conflicto("El pedido #" + p.getNumeroDiario() + " está " + p.getEstado() + " y ya no se puede modificar");
        }
        return p;
    }

    // ===================== Modificar =====================

    /** Si el pedido ya está en cocina, lo agregado sale enseguida como comanda ADICIONAL. */
    @Transactional
    public PedidoResponse agregarItems(Long id, List<ItemRequest> items, UsuarioActual actor) {
        Pedido p = pedidoEditable(id);
        List<DetallePedido> nuevas = agregarItems(p, items);
        boolean enCocina = p.getEstado() != EstadoPedido.ABIERTO;
        if (enCocina) {
            nuevas.forEach(d -> d.setEnviadoCocina(true));
            comanda(p, TipoComanda.ADICIONAL, nuevas, actor);
        }
        pedidos.flush();                      // para que las líneas nuevas tengan id en la respuesta
        anotar(p, TipoEvento.PRODUCTOS_AGREGADOS,
                "Agregó " + resumen(nuevas) + (enCocina ? " (salió comanda adicional a cocina)" : ""), actor, false);
        return PedidoResponse.de(p);
    }

    /**
     * Quita una línea. Si ya se había enviado a cocina, solo puede hacerlo caja o gerencia,
     * la cocina recibe una comanda de CANCELADO y la bitácora lo marca como alerta.
     */
    @Transactional
    public PedidoResponse quitarItem(Long id, Long detalleId, UsuarioActual actor) {
        Pedido p = pedidoEditable(id);
        DetallePedido d = p.getDetalles().stream().filter(x -> x.getId().equals(detalleId)).findFirst()
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Ese producto no está en el pedido"));
        if (d.isEnviadoCocina() && !actor.esCaja()) {
            throw ErrorNegocio.prohibido("Ese plato ya se envió a cocina. Solo el cajero o el gerente puede quitarlo.");
        }
        if (p.getDetalles().size() == 1) {
            throw ErrorNegocio.invalido("No se puede dejar el pedido vacío; si no va, anúlalo");
        }
        p.quitarDetalle(d);
        if (p.getTotalUsd().compareTo(p.getPagadoUsd()) < 0) {
            throw ErrorNegocio.conflicto("No se puede quitar: lo ya pagado quedaría por encima del total");
        }
        String texto = "Quitó " + d.getCantidad() + " x " + d.getNombreProducto() + " ($" + monto(d.getSubtotalUsd()) + ")";
        if (d.isEnviadoCocina()) {
            comanda(p, TipoComanda.CANCELADO, List.of(d), actor);
            texto += " después de enviarlo a cocina";
        }
        anotar(p, TipoEvento.PRODUCTO_QUITADO, texto, actor, d.isEnviadoCocina());
        return PedidoResponse.de(p);
    }

    /**
     * Avanza el estado un paso (ABIERTO → EN_PREPARACION → LISTO → ENTREGADO).
     * Al pasar a EN_PREPARACION sale la comanda a cocina. Un pedido web solo lo acepta (envía a cocina)
     * el cajero o la gerencia, que antes confirman el pago o llaman al cliente.
     */
    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevo, UsuarioActual actor) {
        Pedido p = pedido(id);
        if (nuevo == EstadoPedido.ANULADO) throw ErrorNegocio.invalido("Para anular usa la opción Anular");
        if (nuevo == EstadoPedido.PAGADO) throw ErrorNegocio.invalido("El pedido se cierra solo al quedar entregado y pagado");
        EstadoPedido esperado = SIGUIENTE.get(p.getEstado());
        if (esperado == null || nuevo != esperado) {
            throw ErrorNegocio.conflicto("No se puede pasar de " + p.getEstado() + " a " + nuevo);
        }
        if (nuevo == EstadoPedido.EN_PREPARACION && p.getTipo() == TipoPedido.WEB && !actor.esCaja()) {
            throw ErrorNegocio.prohibido("Los pedidos de la web los acepta el cajero");
        }
        p.setEstado(nuevo);
        String texto = switch (nuevo) {
            case EN_PREPARACION -> p.getTipo() == TipoPedido.WEB ? "Aceptó el pedido web y lo envió a cocina" : "Lo envió a cocina";
            case LISTO -> "Lo marcó listo";
            case ENTREGADO -> "Lo marcó entregado";
            default -> "Cambió el estado a " + nuevo;
        };
        if (nuevo == EstadoPedido.EN_PREPARACION) {
            List<DetallePedido> pendientes = p.getDetalles().stream().filter(d -> !d.isEnviadoCocina()).toList();
            pendientes.forEach(d -> d.setEnviadoCocina(true));
            if (!pendientes.isEmpty()) comanda(p, TipoComanda.NUEVA, pendientes, actor);
        }
        cerrarSiCorresponde(p);
        anotar(p, TipoEvento.ESTADO, texto, actor, false);
        return PedidoResponse.de(p);
    }

    /** Vuelve a imprimir en cocina lo que ya se envió (marcado como REIMPRESIÓN para que no se prepare dos veces). */
    @Transactional
    public PedidoResponse reimprimir(Long id, UsuarioActual actor) {
        Pedido p = pedido(id);
        if (p.getEstado() == EstadoPedido.ANULADO) throw ErrorNegocio.conflicto("El pedido está anulado");
        List<DetallePedido> enviados = p.getDetalles().stream().filter(DetallePedido::isEnviadoCocina).toList();
        if (enviados.isEmpty()) throw ErrorNegocio.conflicto("Este pedido todavía no se ha enviado a cocina");
        comanda(p, TipoComanda.REIMPRESION, enviados, actor);
        anotar(p, TipoEvento.REIMPRESION, "Reimprimió la comanda en cocina", actor, true);
        return PedidoResponse.de(p);
    }

    @Transactional
    public PedidoResponse anular(Long id, String motivo, UsuarioActual actor) {
        if (!actor.esGerencia()) {
            throw ErrorNegocio.prohibido("Solo un gerente o el administrador puede anular pedidos");
        }
        Pedido p = pedido(id);
        if (p.getEstado() == EstadoPedido.ANULADO || p.getEstado() == EstadoPedido.PAGADO) {
            throw ErrorNegocio.conflicto("El pedido ya está " + p.getEstado());
        }
        if (!p.getPagos().isEmpty()) {
            throw ErrorNegocio.conflicto("El pedido tiene pagos registrados; no se puede anular");
        }
        boolean enCocina = p.getEstado() == EstadoPedido.EN_PREPARACION || p.getEstado() == EstadoPedido.LISTO;
        List<DetallePedido> enviados = p.getDetalles().stream().filter(DetallePedido::isEnviadoCocina).toList();
        p.setEstado(EstadoPedido.ANULADO);
        p.setMotivoAnulacion(motivo.trim() + " (anuló " + actor.username() + ")");
        p.setCerradoEn(LocalDateTime.now(reloj));
        if (enCocina && !enviados.isEmpty()) comanda(p, TipoComanda.ANULADO, enviados, actor);
        anotar(p, TipoEvento.ANULADO, "Anuló el pedido: " + motivo.trim(), actor, true);
        return PedidoResponse.de(p);
    }

    // ===================== Pagos =====================

    @Transactional
    public PedidoResponse registrarPago(Long id, PagoRequest req, UsuarioActual actor) {
        if (!actor.esCaja()) {
            throw ErrorNegocio.prohibido("Solo el cajero o el gerente puede cobrar");
        }
        Pedido p = pedidoEditable(id);
        Turno turno = turnos.turnoAbierto()
                .orElseThrow(() -> ErrorNegocio.conflicto("No hay un turno abierto para registrar el cobro"));

        if (req.metodo() != MetodoPago.EFECTIVO_USD && req.metodo() != MetodoPago.EFECTIVO_BS
                && req.metodo() != MetodoPago.PUNTO_DE_VENTA && limpio(req.referencia()) == null) {
            throw ErrorNegocio.invalido("Indica la referencia del " + req.metodo());
        }

        BigDecimal tasa = p.getTasaBsPorUsd();
        if (tasa == null) {
            tasa = tasas.tasaVigente().orElse(null);
            if (tasa == null && req.metodo().getMoneda() == Moneda.BS) {
                throw ErrorNegocio.invalido("Para cobrar en bolívares primero registra una tasa de cambio");
            }
            if (tasa != null) p.asignarTasa(tasa);   // desde ahora el pedido también vale en Bs
        }

        Pago pago = Pago.de(req.metodo(), req.monto(), tasa, limpio(req.referencia()));
        BigDecimal saldo = p.getSaldoPendienteUsd();
        if (pago.getEquivalenteUsd().compareTo(saldo.add(TOLERANCIA_USD)) > 0) {
            throw ErrorNegocio.invalido("El pago ($" + pago.getEquivalenteUsd() + ") supera el saldo pendiente ($"
                    + saldo + "). Registra solo lo que se cobra; el vuelto no se registra.");
        }
        pago.setTurno(turno);
        pago.setRegistradoPor(usuarios.referencia(actor.id()));
        p.agregarPago(pago);
        cerrarSiCorresponde(p);
        pedidos.flush();
        String cobrado = (req.metodo().getMoneda() == Moneda.USD ? "$" : "Bs ") + monto(pago.getMonto());
        anotar(p, TipoEvento.COBRO, "Cobró " + cobrado + " por " + NOMBRE_METODO.get(req.metodo())
                + (pago.getReferencia() != null ? " (ref. " + pago.getReferencia() + ")" : ""), actor, false);
        return PedidoResponse.de(p);
    }

    private static final Map<MetodoPago, String> NOMBRE_METODO = Map.of(
            MetodoPago.EFECTIVO_USD, "efectivo en dólares",
            MetodoPago.ZELLE, "Zelle",
            MetodoPago.EFECTIVO_BS, "efectivo en bolívares",
            MetodoPago.PAGO_MOVIL, "pago móvil",
            MetodoPago.PUNTO_DE_VENTA, "punto de venta",
            MetodoPago.TRANSFERENCIA_BS, "transferencia");

    // ===================== Bitácora y cocina =====================

    /** 1234.5 → "1.234,50", como se ven los montos en el POS. */
    static String monto(BigDecimal valor) {
        java.text.DecimalFormatSymbols simbolos = new java.text.DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        return new java.text.DecimalFormat("#,##0.00", simbolos).format(valor);
    }

    /** "2 x Cheese burger, 1 x Refresco" */
    private static String resumen(List<DetallePedido> lineas) {
        String texto = lineas.stream().map(d -> d.getCantidad() + " x " + d.getNombreProducto())
                .reduce((a, b) -> a + ", " + b).orElse("");
        return texto.length() > 400 ? texto.substring(0, 397) + "..." : texto;
    }

    private void anotar(Pedido p, TipoEvento tipo, String detalle, UsuarioActual actor, boolean alerta) {
        Long turnoId = turnos.turnoAbierto().map(Turno::getId).orElse(p.getTurno().getId());
        bitacora.save(EventoPedido.builder()
                .pedido(p)
                .turnoId(turnoId)
                .tipo(tipo)
                .detalle(detalle.length() > 500 ? detalle.substring(0, 500) : detalle)
                .alerta(alerta)
                .usuario(actor == null || actor.id() == null ? null : usuarios.referencia(actor.id()))
                .build());
    }

    /** Avisa al módulo cocina (comanda impresa). Va en la misma transacción que el cambio del pedido. */
    private void comanda(Pedido p, TipoComanda tipo, List<DetallePedido> lineas, UsuarioActual actor) {
        String entrega = switch (p.getTipo()) {
            case WEB -> p.getDireccionEntrega() != null ? "DELIVERY" : "RETIRO";
            case DELIVERY -> "DELIVERY";
            default -> null;
        };
        String atendio = p.getMesero() == null ? "Web" : p.getMesero().getNombre();
        String quien = actor == null || actor.id() == null ? null : usuarios.referencia(actor.id()).getNombre();
        eventos.publishEvent(new ComandaSolicitada(p.getId(), p.getNumeroDiario(), tipo, p.getTipo(),
                p.getMesa() == null ? null : p.getMesa().getNumero(), p.getClienteNombre(), entrega, atendio,
                notasParaCocina(p), lineas.stream().map(d -> new Linea(d.getCantidad(), d.getNombreProducto(), d.getNotas(), d.getProducto().getId())).toList(),
                quien));
    }

    /** Notas del pedido sin lo que no le sirve a la cocina ("[Delivery] Pago: Pago Móvil."). */
    static String notasParaCocina(Pedido p) {
        String n = limpio(p.getNotas());
        if (n == null) return null;
        if (p.getTipo() == TipoPedido.WEB) {
            n = n.replaceFirst("^\\[[^\\]]*\\]\\s*", "").replaceFirst("^Pago: [^.]*\\.\\s*", "");
        }
        return limpio(n);
    }

    /** ENTREGADO + saldo ≤ tolerancia ⇒ PAGADO (cerrado). */
    private void cerrarSiCorresponde(Pedido p) {
        if (p.getEstado() == EstadoPedido.ENTREGADO && p.getSaldoPendienteUsd().compareTo(TOLERANCIA_USD) <= 0) {
            p.setEstado(EstadoPedido.PAGADO);
            p.setCerradoEn(LocalDateTime.now(reloj));
        }
    }
}
