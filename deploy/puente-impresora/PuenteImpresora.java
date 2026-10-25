import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Puente entre Ocumare Food (en internet) y una impresora térmica de red común del local (ESC/POS, puerto 9100).
 *
 * Cada pocos segundos le pregunta al sistema si hay una comanda nueva; si la hay, la manda a la impresora
 * por la red del local y le avisa al sistema que salió. Así la comanda sale en cocina venga de donde venga
 * el pedido (mesero, caja o cliente desde la web).
 *
 * No necesita instalar nada más que Java 21. Se arranca con puente-impresora.bat (Windows), o con:
 *   java PuenteImpresora.java <direccion-del-puente> <ip-de-la-impresora> [puerto]
 * La dirección del puente sale en Gestión > Impresoras al agregar la impresora.
 */
public class PuenteImpresora {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final byte ESC = 0x1B, GS = 0x1D;

    public static void main(String[] args) throws Exception {
        String servidor = valor(args, 0, "SERVIDOR");
        String impresora = valor(args, 1, "IMPRESORA");
        int puerto = Integer.parseInt(opcional(args, 2, "PUERTO", "9100"));
        int cada = Integer.parseInt(opcional(args, 3, "CADA_SEGUNDOS", "3"));
        if (servidor == null || impresora == null) {
            System.out.println("Falta la configuración. Uso: java PuenteImpresora.java <direccion-del-puente> <ip-de-la-impresora> [puerto]");
            System.exit(1);
        }
        servidor = servidor.replaceAll("/+$", "");
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        log("Puente listo. Impresora " + impresora + ":" + puerto + ". Revisando cada " + cada + " s. Cierra esta ventana para detenerlo.");

        boolean avisoSinInternet = false;
        while (true) {
            try {
                HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(servidor + "/siguiente"))
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (avisoSinInternet) { log("Conexión con el sistema recuperada."); avisoSinInternet = false; }
                if (r.statusCode() == 404) {
                    log("El sistema no reconoce esta impresora (dirección mal copiada o impresora desactivada). Reviso de nuevo en 1 minuto.");
                    Thread.sleep(60_000);
                    continue;
                }
                if (r.statusCode() == 200) {
                    String id = r.headers().firstValue("X-Comanda-Id").orElse(null);
                    String texto = new String(r.body(), StandardCharsets.US_ASCII);
                    boolean ok;
                    String estado = null;
                    try {
                        imprimir(impresora, puerto, texto);
                        ok = true;
                        log("Comanda " + id + " impresa.");
                    } catch (IOException e) {
                        ok = false;
                        estado = "No se pudo imprimir: " + e.getMessage();
                        log(estado + " ¿Está prendida la impresora y en la misma red?");
                    }
                    if (id != null) {
                        String url = servidor + "/" + id + "?ok=" + ok
                                + (estado == null ? "" : "&estado=" + URLEncoder.encode(estado, StandardCharsets.UTF_8));
                        http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                                .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
                    }
                    if (!ok) Thread.sleep(10_000);
                    continue;    // puede haber otra en cola: preguntar enseguida
                }
                if (r.statusCode() != 204) log("El sistema respondió " + r.statusCode() + ". Reintento.");
            } catch (IOException e) {
                if (!avisoSinInternet) {
                    log("Sin conexión con el sistema (" + e.getMessage() + "). Sigo intentando.");
                    avisoSinInternet = true;
                }
            }
            Thread.sleep(cada * 1000L);
        }
    }

    /** Manda la hoja a la impresora: letra grande donde viene marcada, avance de papel y corte. */
    static void imprimir(String ip, int puerto, String texto) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, puerto), 5000);
            s.setSoTimeout(10000);
            OutputStream out = s.getOutputStream();
            out.write(new byte[]{ESC, '@'});                       // reiniciar
            for (String linea : texto.split("\n", -1)) {
                byte modo = 0;
                if (linea.startsWith("##2 ")) { modo = 0x30; linea = linea.substring(4); }   // doble alto y ancho
                else if (linea.startsWith("##1 ")) { modo = 0x10; linea = linea.substring(4); }  // doble alto
                if (modo != 0) out.write(new byte[]{ESC, '!', modo});
                out.write(linea.getBytes(StandardCharsets.US_ASCII));
                if (modo != 0) out.write(new byte[]{ESC, '!', 0});
                out.write('\n');
            }
            out.write(new byte[]{ESC, 'd', 4});                    // avanzar 4 líneas
            out.write(new byte[]{GS, 'V', 66, 0});                  // corte parcial
            out.flush();
        }
    }

    private static String valor(String[] args, int i, String variable) {
        if (args.length > i && !args[i].isBlank()) return args[i].trim();
        String v = System.getenv(variable);
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String opcional(String[] args, int i, String variable, String porDefecto) {
        String v = valor(args, i, variable);
        return v == null ? porDefecto : v;
    }

    private static void log(String texto) {
        System.out.println("[" + LocalTime.now().format(HORA) + "] " + texto);
    }
}
