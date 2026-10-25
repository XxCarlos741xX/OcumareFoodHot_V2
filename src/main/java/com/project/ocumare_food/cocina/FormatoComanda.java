package com.project.ocumare_food.cocina;

import com.project.ocumare_food.cocina.model.Comanda;
import com.project.ocumare_food.cocina.model.LineaComanda;
import com.project.ocumare_food.cocina.model.TipoComandaCocina;
import com.project.ocumare_food.pedidos.model.TipoPedido;

import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte una comanda en el texto que imprime la térmica de cocina.
 *
 * Solo usa letras sin tilde (ASCII): las impresoras baratas traen tablas de caracteres distintas y
 * una "ñ" puede salir como un símbolo raro. "Piña" sale "Pina", que en cocina se entiende igual.
 *
 * Con {@code marcas = true} (lo usa el puente) algunas líneas empiezan con un código que el puente
 * convierte en letra grande antes de mandarla a la impresora:
 *   "##2 " = doble de alto y de ancho (cabe la mitad de caracteres), "##1 " = doble de alto.
 * Sin marcas (impresora con nube, texto plano) todo sale en tamaño normal.
 */
public final class FormatoComanda {

    public static final String GRANDE = "##2 ";
    public static final String ALTA = "##1 ";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private FormatoComanda() {
    }

    public static String texto(Comanda c, int columnas, boolean marcas) {
        int ancho = Math.max(24, Math.min(64, columnas));
        List<String> out = new ArrayList<>();
        String grande = marcas ? GRANDE : "";
        String alta = marcas ? ALTA : "";
        int anchoGrande = marcas ? ancho / 2 : ancho;
        String raya = "-".repeat(ancho);

        if (c.getTipo() == TipoComandaCocina.PRUEBA) {
            out.add(grande + recortar("PRUEBA", anchoGrande));
            out.add("Si lees esto, la impresora de cocina");
            out.add("esta bien conectada a Ocumare Food.");
            out.add(HORA.format(c.getCreadaEn()));
            return unir(out);
        }

        out.add(grande + recortar("#" + c.getNumeroPedido() + " " + origen(c), anchoGrande));
        out.add(alta + titulo(c.getTipo()));
        String aviso = aviso(c.getTipo());
        if (aviso != null) out.add(ascii(aviso));
        if (c.getClienteNombre() != null && c.getTipoPedido() != TipoPedido.MESA) {
            out.addAll(envolver("Cliente: " + ascii(c.getClienteNombre()), ancho, ""));
        }
        String atiende = "Atiende: " + ascii(c.getAtendidoPor() == null ? "-" : c.getAtendidoPor());
        String hora = HORA.format(c.getCreadaEn());
        out.add(atiende.length() + hora.length() + 1 <= ancho
                ? atiende + " ".repeat(ancho - atiende.length() - hora.length()) + hora
                : recortar(atiende, ancho));
        if (atiende.length() + hora.length() + 1 > ancho) out.add(hora);
        if (c.getSolicitadoPor() != null && c.getTipo() != TipoComandaCocina.NUEVA
                && c.getTipo() != TipoComandaCocina.ADICIONAL) {
            out.add(recortar("Pidio: " + ascii(c.getSolicitadoPor()), ancho));
        }
        out.add(raya);

        boolean quitar = c.getTipo() == TipoComandaCocina.CANCELADO || c.getTipo() == TipoComandaCocina.ANULADO;
        for (LineaComanda l : c.getLineas()) {
            String linea = (quitar ? "NO: " : "") + l.getCantidad() + " x " + ascii(l.getNombre());
            List<String> partes = envolver(linea, ancho, "    ");
            for (int i = 0; i < partes.size(); i++) out.add((i == 0 ? alta : "") + partes.get(i));
            if (l.getNotas() != null && !l.getNotas().isBlank()) {
                out.addAll(envolver("  > " + ascii(l.getNotas()), ancho, "    "));
            }
        }
        out.add(raya);
        if (c.getNotasPedido() != null && !c.getNotasPedido().isBlank()
                && c.getTipo() != TipoComandaCocina.CANCELADO && c.getTipo() != TipoComandaCocina.ANULADO) {
            out.addAll(envolver("NOTA: " + ascii(c.getNotasPedido()), ancho, "  "));
        }
        return unir(out);
    }

    static String origen(Comanda c) {
        if (c.getTipoPedido() == null) return "";
        return switch (c.getTipoPedido()) {
            case MESA -> "MESA " + c.getMesaNumero();
            case PARA_LLEVAR -> "LLEVAR";
            case DELIVERY -> "DELIVERY";
            case WEB -> "DELIVERY".equals(c.getEntrega()) ? "WEB DELIVERY" : "WEB RETIRA";
        };
    }

    static String titulo(TipoComandaCocina tipo) {
        return switch (tipo) {
            case NUEVA -> "NUEVO PEDIDO";
            case ADICIONAL -> "ADICIONAL";
            case CANCELADO -> "*** CANCELAR ***";
            case ANULADO -> "*** PEDIDO ANULADO ***";
            case REIMPRESION -> "REIMPRESION";
            case PRUEBA -> "PRUEBA";
        };
    }

    static String aviso(TipoComandaCocina tipo) {
        return switch (tipo) {
            case ADICIONAL -> "Agregar al pedido que ya esta en cocina";
            case CANCELADO -> "Quitaron estos platos del pedido";
            case ANULADO -> "No preparar nada de este pedido";
            case REIMPRESION -> "Copia: ya se envio, no preparar de nuevo";
            default -> null;
        };
    }

    /** Quita tildes y cualquier carácter que no sea ASCII imprimible. */
    public static String ascii(String s) {
        if (s == null) return "";
        String sinTildes = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder(sinTildes.length());
        for (char ch : sinTildes.toCharArray()) {
            if (ch >= 32 && ch < 127) sb.append(ch);
            else if (Character.isWhitespace(ch)) sb.append(' ');
        }
        return sb.toString().replaceAll(" {2,}", " ").trim();
    }

    static String recortar(String s, int ancho) {
        return s.length() <= ancho ? s : s.substring(0, ancho);
    }

    /** Parte un texto largo en líneas del ancho del papel, sin cortar palabras si se puede. */
    static List<String> envolver(String texto, int ancho, String sangria) {
        List<String> lineas = new ArrayList<>();
        String resto = texto;
        boolean primera = true;
        while (!resto.isEmpty()) {
            String prefijo = primera ? "" : sangria;
            int cabe = ancho - prefijo.length();
            if (resto.length() <= cabe) {
                lineas.add(prefijo + resto);
                break;
            }
            int corte = resto.lastIndexOf(' ', cabe);
            if (corte <= 0) corte = cabe;
            lineas.add(prefijo + resto.substring(0, corte).stripTrailing());
            resto = resto.substring(corte).stripLeading();
            primera = false;
        }
        return lineas;
    }

    private static String unir(List<String> lineas) {
        return String.join("\n", lineas) + "\n";
    }
}
