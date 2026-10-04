package com.project.ocumare_food.repository;

import com.project.ocumare_food.model.*;
import com.project.ocumare_food.model.enums.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifica que el modelo se guarda y se lee completo contra una base real (H2 en modo MySQL). */
@DataJpaTest
class ModeloPersistenciaTest {

    @Autowired EntityManager em;
    @Autowired PedidoRepository pedidos;
    @Autowired TasaCambioRepository tasas;
    @Autowired ProductoRepository productos;

    private final BigDecimal tasa = new BigDecimal("36.5000");

    private Usuario mesero;
    private Turno turno;
    private Mesa mesa;
    private Producto hamburguesa;

    private void datosBase() {
        mesero = Usuario.builder().nombre("Ana").username("ana").passwordHash("x").rol(Rol.MESERO).build();
        em.persist(mesero);
        turno = Turno.builder().tipo(TipoTurno.DIA).fechaOperativa(LocalDate.of(2026, 10, 3))
                .abiertoEn(LocalDateTime.now()).abiertoPor(mesero).build();
        em.persist(turno);
        mesa = Mesa.builder().numero(1).build();
        em.persist(mesa);
        Categoria cat = Categoria.builder().nombre("Hamburguesas").orden(1).build();
        em.persist(cat);
        hamburguesa = Producto.builder().nombre("Hamburguesa").precioUsd(new BigDecimal("5.50")).categoria(cat).build();
        em.persist(hamburguesa);
    }

    private Pedido pedido(int numero) {
        return Pedido.builder().numeroDiario(numero).fechaOperativa(turno.getFechaOperativa())
                .tipo(TipoPedido.MESA).turno(turno).mesa(mesa).mesero(mesero).tasaBsPorUsd(tasa).build();
    }

    @Test
    void guardaPedidoConDetallesYPagosEnCascada() {
        datosBase();
        Pedido p = pedido(1);
        p.agregarDetalle(DetallePedido.de(hamburguesa, 2, "sin cebolla"));
        Pago pago = Pago.de(MetodoPago.EFECTIVO_USD, new BigDecimal("11"), tasa, null);
        pago.setTurno(turno);
        p.agregarPago(pago);
        Long id = pedidos.saveAndFlush(p).getId();
        em.clear();

        Pedido leido = pedidos.findById(id).orElseThrow();
        assertThat(leido.getDetalles()).hasSize(1);
        assertThat(leido.getPagos()).hasSize(1);
        assertThat(leido.getTotalUsd()).isEqualByComparingTo("11.00");
        assertThat(leido.getTotalBs()).isEqualByComparingTo("401.50");
        assertThat(leido.getMesero().getUsername()).isEqualTo("ana");
        assertThat(leido.getSaldoPendienteUsd()).isEqualByComparingTo("0");
        assertThat(pedidos.maxNumeroDiario(turno.getFechaOperativa())).isEqualTo(1);
    }

    @Test
    void noPermiteNumeroDiarioRepetidoEnElMismoDia() {
        datosBase();
        pedidos.saveAndFlush(pedido(7));
        assertThatThrownBy(() -> pedidos.saveAndFlush(pedido(7)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laClaveDeLaWebEvitaPedidosDuplicados() {
        datosBase();
        Pedido web1 = pedido(1);
        web1.setClaveExterna("abc-123");
        pedidos.saveAndFlush(web1);
        assertThat(pedidos.findByClaveExterna("abc-123")).isPresent();

        Pedido web2 = pedido(2);
        web2.setClaveExterna("abc-123");
        assertThatThrownBy(() -> pedidos.saveAndFlush(web2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void tomaLaTasaVigenteMasReciente() {
        LocalDateTime ayer = LocalDateTime.now().minusDays(1);
        tasas.save(TasaCambio.builder().bsPorUsd(new BigDecimal("36.0")).vigenteDesde(ayer).fuente("BCV").build());
        tasas.save(TasaCambio.builder().bsPorUsd(new BigDecimal("36.5")).vigenteDesde(ayer.plusHours(20)).fuente("BCV").build());
        tasas.save(TasaCambio.builder().bsPorUsd(new BigDecimal("99")).vigenteDesde(LocalDateTime.now().plusDays(1)).fuente("futura").build());

        BigDecimal vigente = tasas.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDescIdDesc(LocalDateTime.now())
                .orElseThrow().getBsPorUsd();
        assertThat(vigente).isEqualByComparingTo("36.5");
    }

    @Test
    void elMenuSoloMuestraProductosActivos() {
        datosBase();
        Producto viejo = Producto.builder().nombre("Descontinuado").precioUsd(BigDecimal.ONE)
                .categoria(hamburguesa.getCategoria()).activo(false).build();
        em.persist(viejo);
        em.flush();

        List<Producto> menu = productos.findByActivoTrueOrderByCategoriaOrdenAscNombreAsc();
        assertThat(menu).extracting(Producto::getNombre).containsExactly("Hamburguesa");
    }
}
