package com.project.ocumare_food.cocina;

import com.project.ocumare_food.cocina.model.Comanda;
import com.project.ocumare_food.cocina.model.LineaComanda;
import com.project.ocumare_food.cocina.model.TipoComandaCocina;
import com.project.ocumare_food.pedidos.model.TipoPedido;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormatoComandaTest {

    private Comanda comanda(TipoComandaCocina tipo) {
        return Comanda.builder().tipo(tipo).numeroPedido(7).tipoPedido(TipoPedido.WEB).entrega("DELIVERY")
                .clienteNombre("María Peña").atendidoPor("Web").solicitadoPor("Carla Caja")
                .notasPedido("Tocar el timbre dos veces, la casa es la del portón verde al lado de la panadería")
                .lineas(List.of(new LineaComanda(2, "Hamburguesa Ocumare Gourmet doble con tocineta y queso", "sin cebolla"),
                        new LineaComanda(1, "Piña colada", null)))
                .creadaEn(LocalDateTime.of(2026, 10, 25, 15, 41)).build();
    }

    @Test
    void quitaTildesYCaracteresRaros() {
        assertThat(FormatoComanda.ascii("Piña ñandú ¡ya! 🍔")).isEqualTo("Pina nandu ya!");
    }

    @Test
    void ningunaLineaPasaDelAnchoDelPapel() {
        for (int ancho : new int[]{32, 42, 48}) {
            String texto = FormatoComanda.texto(comanda(TipoComandaCocina.NUEVA), ancho, false);
            assertThat(texto.lines()).allSatisfy(l -> assertThat(l.length()).isLessThanOrEqualTo(ancho));
            assertThat(texto).contains("#7 WEB DELIVERY", "NUEVO PEDIDO", "Cliente: Maria Pena", "1 x Pina colada", "15:41");
        }
    }

    @Test
    void elCanceladoDiceQueNoSePrepara() {
        String texto = FormatoComanda.texto(comanda(TipoComandaCocina.CANCELADO), 42, true);
        assertThat(texto).contains("##1 *** CANCELAR ***", "NO: 2 x", "Pidio: Carla Caja").doesNotContain("NOTA:");
        String grande = texto.lines().filter(l -> l.startsWith(FormatoComanda.GRANDE)).findFirst().orElseThrow();
        assertThat(grande.length() - FormatoComanda.GRANDE.length()).isLessThanOrEqualTo(21);
    }
}
