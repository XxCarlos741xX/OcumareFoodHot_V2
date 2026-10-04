package com.project.ocumare_food.service;

import com.project.ocumare_food.model.DetallePedido;
import com.project.ocumare_food.model.Pedido;
import com.project.ocumare_food.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
 */
@Service
public class NotificadorPedidos {

    private static final Logger log = LoggerFactory.getLogger(NotificadorPedidos.class);

    private final PedidoRepository pedidos;
    private final String token;
    private final String chatId;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public NotificadorPedidos(PedidoRepository pedidos,
                              @Value("${ocumare.telegram.token:}") String token,
                              @Value("${ocumare.telegram.chat-id:}") String chatId) {
        this.pedidos = pedidos;
        this.token = token == null ? "" : token.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
    }

    public boolean activo() {
        return !token.isEmpty() && !chatId.isEmpty();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void alRecibirPedidoWeb(PedidoService.PedidoWebRecibido evento) {
        if (!activo()) return;
        pedidos.findById(evento.pedidoId()).ifPresent(p -> {
            String texto = mensaje(p);
            Thread.ofVirtual().start(() -> enviar(texto));
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
