package com.project.ocumare_food.cocina;

import com.project.ocumare_food.cocina.dto.CocinaDto.*;
import com.project.ocumare_food.cocina.model.Comanda;
import com.project.ocumare_food.cocina.model.Impresora;
import com.project.ocumare_food.cocina.model.LineaComanda;
import com.project.ocumare_food.cocina.model.TipoComandaCocina;
import com.project.ocumare_food.cocina.repository.ComandaRepository;
import com.project.ocumare_food.cocina.repository.ImpresoraRepository;
import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.pedidos.ComandaSolicitada;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Cola de impresión de la cocina.
 *
 * Cada vez que pedidos publica una ComandaSolicitada (pedido enviado a cocina, platos agregados o quitados,
 * anulación, reimpresión) se guarda una Comanda pendiente. La impresora la viene a buscar sola:
 *  - Impresora con nube (Star CloudPRNT): pregunta cada pocos segundos a /api/impresion/cloudprnt/{token}.
 *  - Impresora de red común: un "puente" en el local (PC o equipo con Java) pregunta a /api/impresion/puente/{token}
 *    y le manda la hoja a la impresora por la red del local.
 * Así se imprime igual si el pedido lo tomó un mesero, la caja o un cliente desde su casa.
 *
 * Las comandas pendientes de más de {@link #VIGENCIA} no se imprimen (al conectar una impresora nueva no sale
 * todo lo viejo de golpe). Si la impresora reporta {@link #MAX_FALLOS} fallos seguidos con una hoja, se deja
 * de intentar; desde el pedido se puede reimprimir.
 */
@Service
public class CocinaService {

    static final Duration VIGENCIA = Duration.ofHours(2);
    static final int MAX_FALLOS = 3;
    /** Si la impresora no pregunta en este tiempo, el POS avisa que está desconectada. */
    static final Duration CONECTADA = Duration.ofMinutes(2);

    private final ComandaRepository comandas;
    private final ImpresoraRepository impresoras;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public CocinaService(ComandaRepository comandas, ImpresoraRepository impresoras, Clock reloj) {
        this.comandas = comandas;
        this.impresoras = impresoras;
        this.reloj = reloj;
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }

    // ===================== Entrada: lo que manda pedidos =====================

    /** Corre dentro de la transacción del pedido: si el pedido no se guarda, la comanda tampoco. */
    @EventListener
    public void alSolicitarComanda(ComandaSolicitada e) {
        Comanda c = Comanda.builder()
                .pedidoId(e.pedidoId())
                .numeroPedido(e.numeroPedido())
                .tipo(TipoComandaCocina.valueOf(e.tipo().name()))
                .tipoPedido(e.tipoPedido())
                .mesaNumero(e.mesaNumero())
                .clienteNombre(corto(e.clienteNombre(), 100))
                .entrega(e.entrega())
                .atendidoPor(corto(e.atendidoPor(), 100))
                .solicitadoPor(corto(e.solicitadoPor(), 100))
                .notasPedido(corto(e.notasPedido(), 500))
                .lineas(e.lineas().stream()
                        .map(l -> new LineaComanda(l.cantidad(), corto(l.nombre(), 120), corto(l.notas(), 200)))
                        .toList())
                .creadaEn(ahora())
                .build();
        comandas.save(c);
    }

    private static String corto(String s, int max) {
        return s == null ? null : s.length() <= max ? s : s.substring(0, max);
    }

    // ===================== Salida: lo que pide la impresora =====================

    /** Busca la impresora por su token y anota que se conectó. 404 si el token no existe o se desactivó. */
    @Transactional
    public Impresora conectar(String token, String via, String estado) {
        Impresora i = impresoras.findByTokenHashAndActivaTrue(hash(token))
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Impresora no encontrada"));
        i.setUltimaConexion(ahora());
        i.setUltimaVia(via);
        if (estado != null && !estado.isBlank()) i.setUltimoEstado(corto(estado.trim(), 120));
        return i;
    }

    @Transactional(readOnly = true)
    public Optional<Comanda> siguiente() {
        return comandas.findFirstByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfterOrderByIdAsc(ahora().minus(VIGENCIA));
    }

    /** La impresora confirmó la hoja: impresa (ok) o falló (sin papel, tapa abierta…). */
    @Transactional
    public void confirmar(Long comandaId, Impresora impresora, boolean ok) {
        comandas.findById(comandaId).ifPresent(c -> {
            if (c.getImpresaEn() != null) return;
            if (ok) {
                c.setImpresaEn(ahora());
                c.setImpresoraId(impresora.getId());
            } else {
                c.setFallos(c.getFallos() + 1);
                if (c.getFallos() >= MAX_FALLOS) c.setFallidaEn(ahora());
            }
        });
    }

    // ===================== Lo que ve el personal =====================

    @Transactional(readOnly = true)
    public List<ComandaResponse> dePedido(Long pedidoId) {
        return comandas.findByPedidoIdOrderByIdAsc(pedidoId).stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public List<ComandaResponse> recientes() {
        return comandas.findTop30ByOrderByIdDesc().stream().map(this::respuesta).toList();
    }

    /** Resumen para avisar en el POS si hay hojas que no salen o la impresora se desconectó. */
    @Transactional(readOnly = true)
    public EstadoCocina estado() {
        LocalDateTime desde = ahora().minus(VIGENCIA);
        List<Impresora> activas = impresoras.findByActivaTrue();
        LocalDateTime ultima = activas.stream().map(Impresora::getUltimaConexion)
                .filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        LocalDateTime masVieja = comandas
                .findFirstByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfterOrderByCreadaEnAsc(desde)
                .map(Comanda::getCreadaEn).orElse(null);
        LocalDateTime ahora = ahora();
        return new EstadoCocina(activas.size(), ultima, conectada(ultima),
                ultima == null ? null : Math.max(0, Duration.between(ultima, ahora).toMinutes()),
                comandas.countByImpresaEnIsNullAndFallidaEnIsNullAndCreadaEnAfter(desde), masVieja,
                masVieja == null ? null : Math.max(0, Duration.between(masVieja, ahora).toMinutes()));
    }

    private boolean conectada(LocalDateTime ultima) {
        return ultima != null && ultima.isAfter(ahora().minus(CONECTADA));
    }

    private ComandaResponse respuesta(Comanda c) {
        String estado;
        if (c.getImpresaEn() != null) estado = "IMPRESA";
        else if (c.getFallidaEn() != null) estado = "FALLIDA";
        else if (c.getCreadaEn().isBefore(ahora().minus(VIGENCIA))) estado = "VENCIDA";
        else estado = "PENDIENTE";
        return new ComandaResponse(c.getId(), c.getPedidoId(), c.getNumeroPedido(), c.getTipo(), estado,
                c.getCreadaEn(), c.getImpresaEn(), c.getFallos());
    }

    // ===================== Impresoras (gerencia) =====================

    @Transactional(readOnly = true)
    public List<ImpresoraResponse> impresoras() {
        return impresoras.findAllByOrderByIdAsc().stream().map(this::respuesta).toList();
    }

    /** Registra una impresora y devuelve su token. El token solo se muestra esta vez. */
    @Transactional
    public ImpresoraCreada crearImpresora(CrearImpresoraRequest req) {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Impresora i = impresoras.save(Impresora.builder()
                .nombre(req.nombre().trim())
                .tokenHash(hash(token))
                .columnas(req.columnas() == null ? 48 : req.columnas())
                .creadaEn(ahora())
                .build());
        return new ImpresoraCreada(respuesta(i), token,
                "/api/impresion/cloudprnt/" + token, "/api/impresion/puente/" + token);
    }

    @Transactional
    public ImpresoraResponse desactivar(Long id) {
        Impresora i = impresoras.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Impresora no encontrada"));
        i.setActiva(false);
        return respuesta(i);
    }

    /** Deja en la cola una hoja de prueba (la imprime la primera impresora que pregunte). */
    @Transactional
    public ComandaResponse prueba() {
        if (impresoras.findByActivaTrue().isEmpty()) {
            throw ErrorNegocio.conflicto("Primero agrega una impresora");
        }
        return respuesta(comandas.save(Comanda.builder().tipo(TipoComandaCocina.PRUEBA).creadaEn(ahora()).build()));
    }

    private ImpresoraResponse respuesta(Impresora i) {
        return new ImpresoraResponse(i.getId(), i.getNombre(), i.getColumnas(), i.isActiva(), i.getCreadaEn(),
                i.getUltimaConexion(), i.getUltimaVia(), i.getUltimoEstado(), i.isActiva() && conectada(i.getUltimaConexion()));
    }

    static String hash(String token) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(
                    (token == null ? "" : token).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
