package com.project.ocumare_food.model;

import com.project.ocumare_food.model.enums.MetodoPago;
import com.project.ocumare_food.model.enums.Moneda;
import com.project.ocumare_food.model.enums.TipoPedido;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pruebas de la lógica de dinero sin base de datos. */
class MontosPedidoTest {

    private static final BigDecimal TASA = new BigDecimal("36.5000");

    private Producto producto(String nombre, String precio) {
        return Producto.builder().nombre(nombre).precioUsd(new BigDecimal(precio)).build();
    }

    private Pedido pedidoNuevo() {
        return Pedido.builder().tipo(TipoPedido.MESA).tasaBsPorUsd(TASA).build();
    }

    @Test
    void calculaTotalesEnDolaresYBolivares() {
        Pedido pedido = pedidoNuevo();
        pedido.agregarDetalle(DetallePedido.de(producto("Hamburguesa", "5.50"), 2, null));
        pedido.agregarDetalle(DetallePedido.de(producto("Refresco", "1.25"), 1, null));

        assertThat(pedido.getSubtotalUsd()).isEqualByComparingTo("12.25");
        assertThat(pedido.getTotalUsd()).isEqualByComparingTo("12.25");
        assertThat(pedido.getTotalBs()).isEqualByComparingTo("447.13"); // 12.25 × 36.5 = 447.125 → 447.13
    }

    @Test
    void sumaElCostoDeDelivery() {
        Pedido pedido = pedidoNuevo();
        pedido.setCostoDeliveryUsd(new BigDecimal("2.00"));
        pedido.agregarDetalle(DetallePedido.de(producto("Perro", "3.00"), 1, null));

        assertThat(pedido.getTotalUsd()).isEqualByComparingTo("5.00");
        assertThat(pedido.getTotalBs()).isEqualByComparingTo("182.50");
    }

    @Test
    void elDetalleConservaElPrecioAunqueCambieElMenu() {
        Producto hamburguesa = producto("Hamburguesa", "5.50");
        DetallePedido detalle = DetallePedido.de(hamburguesa, 1, null);

        hamburguesa.setPrecioUsd(new BigDecimal("7.00"));
        hamburguesa.setNombre("Hamburguesa XL");

        assertThat(detalle.getPrecioUnitarioUsd()).isEqualByComparingTo("5.50");
        assertThat(detalle.getNombreProducto()).isEqualTo("Hamburguesa");
    }

    @Test
    void pagoMixtoDolaresYBolivares() {
        Pedido pedido = pedidoNuevo();
        pedido.agregarDetalle(DetallePedido.de(producto("Combo", "10.00"), 1, null));

        pedido.agregarPago(Pago.de(MetodoPago.EFECTIVO_USD, new BigDecimal("5"), TASA, null));
        Pago movil = Pago.de(MetodoPago.PAGO_MOVIL, new BigDecimal("182.50"), TASA, "0123");
        pedido.agregarPago(movil);

        assertThat(movil.getMoneda()).isEqualTo(Moneda.BS);
        assertThat(movil.getEquivalenteUsd()).isEqualByComparingTo("5.00");
        assertThat(pedido.getPagadoUsd()).isEqualByComparingTo("10.00");
        assertThat(pedido.getSaldoPendienteUsd()).isEqualByComparingTo("0.00");
    }

    @Test
    void sinTasaElPedidoValeSoloEnDolaresHastaQueSeLeAsigne() {
        Pedido pedido = Pedido.builder().tipo(TipoPedido.PARA_LLEVAR).build();
        pedido.agregarDetalle(DetallePedido.de(producto("Combo", "8.00"), 1, null));
        assertThat(pedido.getTotalUsd()).isEqualByComparingTo("8.00");
        assertThat(pedido.getTotalBs()).isNull();

        pedido.asignarTasa(TASA);
        assertThat(pedido.getTotalBs()).isEqualByComparingTo("292.00");
    }

    @Test
    void rechazaCantidadesYMontosInvalidos() {
        assertThatThrownBy(() -> DetallePedido.de(producto("X", "1"), 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pago.de(MetodoPago.ZELLE, BigDecimal.ZERO, TASA, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Pago.de(MetodoPago.PAGO_MOVIL, BigDecimal.TEN, BigDecimal.ZERO, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
