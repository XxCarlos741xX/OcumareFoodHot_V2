package com.project.ocumare_food.notificaciones;


import com.project.ocumare_food.pedidos.model.DetallePedido;
import com.project.ocumare_food.pedidos.model.Pedido;
import com.project.ocumare_food.pedidos.ConsultasPedidos;
import com.project.ocumare_food.pedidos.PedidoWebRecibido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * Avisa al grupo de Telegram del equipo cuando entra un pedido web (como hacía la versión con Apps Script).
 * Es opcional: si no están TELEGRAM_TOKEN y TELEGRAM_CHAT_ID, no hace nada.
 * Corre después de guardar el pedido y en segundo plano: si Telegram falla, el pedido no se pierde.
 *
 * Importante: el aviso NO abre una transacción dentro del pedido. Antes usaba REQUIRES_NEW, que pedía una
 * segunda conexión mientras el pedido aún tenía la suya; con muchos pedidos web a la vez el pool se llenaba
 * de pedidos esperando una segunda conexión y todo se trababa 30 s (lo encontró la prueba de carga).
 * Ahora, si Telegram está apagado no toca la base, y si está prendido lee el pedido en un hilo aparte.
 */
@Service
public class NotificadorPedidos {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPedidos.class);

    private final ConsultasPedidos pedidos;
    private final TransactionOperations lectura;
    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    public NotificadorPedidos(ConsultasPedidos pedidos, PlatformTransactionManager transacciones,
                              @Value("${ocumare.telegram.token:}") String token,
                              @Value("${ocumare.telegram.chat-id:}") String chatId) {
        this(pedidos, soloLectura(transacciones), token, chatId);
    }

    /** Para pruebas. */
    public NotificadorPedidos(ConsultasPedidos pedidos, String token, String chatId) {
        this(pedidos, TransactionOperations.withoutTransaction(), token, chatId);
    }

    private NotificadorPedidos(ConsultasPedidos pedidos, TransactionOperations lectura, String token, String chatId) {
        this.pedidos = pedidos;
        this.lectura = lectura;
        this.token = token == null ? "" : token.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean activo() {
        return !token.isEmpty() && !chatId.isEmpty();
    }

    private static TransactionOperations soloLectura(PlatformTransactionManager transacciones) {
        TransactionTemplate t = new TransactionTemplate(transacciones);
        t.setReadOnly(true);
        return t;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alRecibirPedidoWeb(PedidoWebRecibido evento) {
        if (!activo()) return;
        // En otro hilo: la petición del cliente termina y suelta su conexión sin esperar a Telegram.
        Thread.ofVirtual().start(() -> {
            try {
                String texto = lectura.execute(estado -> pedidos.buscar(evento.pedidoId()).map(NotificadorPedidos::mensaje).orElse(null));
                if (texto != null) enviar(texto);
            } catch (Exception e) {
                log.warn("No se pudo preparar el aviso del pedido {}: {}", evento.pedidoId(), e.getMessage());
            }
        });
    }

    /** Texto del aviso. Público para poder probarlo. */
    public static String mensaje(Pedido p) {
        String lineas = p.getDetalles().stream()
                .map((DetallePedido d) -> "• " + d.getCantidad() + " x " + d.getNombreProducto())
                .collect(Collectors.joining("\n"));
        StringBuilder sb = new StringBuilder();
        sb.append("🔥 Pedido web #").append(p.getNumeroDiario()).append('\n');
        sb.append("👤 ").append(p.getClienteNombre() == null ? "Sin nombre" : p.getClienteNombre());
        if (p.getClienteTelefono() != null) sb.append(" · ").append(p.getClienteTelefono());
        sb.append('\n');
        sb.append(p.getDireccionEntrega() == null ? "🏪 Retira en el local" : "🛵 " + p.getDireccionEntrega()).append('\n');
        sb.append(lineas).append('\n');
        sb.append("💵 Total: $").append(p.getTotalUsd());
        if (p.getTotalBs() != null) sb.append(" (Bs ").append(p.getTotalBs()).append(')');
        if (p.getNotas() != null) sb.append("\n📝 ").append(p.getNotas());
        if (p.getClienteTelefono() != null) sb.append("\nWhatsApp: https://wa.me/").append(telefonoInternacional(p.getClienteTelefono()));
        return sb.toString();
    }

    /** 0424… → 58424…; +57 300… → 57300…; si ya trae código de país lo deja. */
    static String telefonoInternacional(String tel) {
        String n = tel.replaceAll("\\D", "");
        if (tel.trim().startsWith("+")) return n;
        if (n.startsWith("0")) return "58" + n.substring(1);
        if (n.length() == 10 && n.startsWith("4")) return "58" + n;   // 4241234567
        return n;
    }

    private void enviar(String texto) {
        try {
            String cuerpo = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8)
                    + "&text=" + URLEncoder.encode(texto, StandardCharsets.UTF_8)
                    + "&disable_web_page_preview=true";
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + token + "/sendMessage"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) log.warn("Telegram respondió {}: {}", r.statusCode(), r.body());
        } catch (Exception e) {
            log.warn("No se pudo avisar por Telegram: {}", e.getMessage());
        }
    }
}
