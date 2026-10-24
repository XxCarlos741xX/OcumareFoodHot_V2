package com.project.ocumare_food.salon;

import com.project.ocumare_food.compartido.ErrorNegocio;
import com.project.ocumare_food.salon.dto.MesaDto.MesaRequest;
import com.project.ocumare_food.salon.dto.MesaDto.MesaResponse;
import com.project.ocumare_food.salon.model.Mesa;
import com.project.ocumare_food.salon.repository.MesaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Mesas del local (API pública del módulo salon: pedidos usa {@link #buscar(Long)}).
 * "Eliminar" es una baja lógica. Una mesa ocupada (con pedido abierto) no se renumera ni se desactiva;
 * cuáles están ocupadas lo dice {@link OcupacionMesas}, que implementa el módulo pedidos.
 */
@Service
public class MesaService {

    private final MesaRepository mesas;
    private final OcupacionMesas ocupacion;

    public MesaService(MesaRepository mesas, OcupacionMesas ocupacion) {
        this.mesas = mesas;
        this.ocupacion = ocupacion;
    }

    private static String limpio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Para otros módulos (pedidos): la mesa con ese id, si existe. */
    @Transactional(readOnly = true)
    public Optional<Mesa> buscar(Long id) {
        return mesas.findById(id);
    }

    private List<MesaResponse> respuestaMesas(List<Mesa> lista) {
        Map<Long, Long> ocupadas = ocupacion.mesasOcupadas();
        return lista.stream().map(m -> MesaResponse.de(m, ocupadas.get(m.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<MesaResponse> listarMesas(boolean incluirInactivas) {
        return respuestaMesas(incluirInactivas
                ? mesas.findAllByOrderByOrdenAscNumeroAsc()
                : mesas.findByActivaTrueOrderByOrdenAscNumeroAsc());
    }

    private Mesa mesa(Long id) {
        return mesas.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Mesa no encontrada"));
    }

    @Transactional
    public MesaResponse crearMesa(MesaRequest req) {
        Integer numero = req.numero();
        if (numero == null) {
            numero = mesas.findAll().stream().mapToInt(Mesa::getNumero).max().orElse(0) + 1;
        } else if (mesas.existsByNumero(numero)) {
            throw ErrorNegocio.conflicto("Ya existe la mesa " + numero);
        }
        Mesa m = Mesa.builder()
                .numero(numero)
                .nombre(limpio(req.nombre()))
                .capacidad(req.capacidad() == null ? 4 : req.capacidad())
                .zona(limpio(req.zona()))
                .orden(req.orden() == null ? mesas.maxOrden() + 1 : req.orden())
                .activa(req.activa() == null || req.activa())
                .build();
        return MesaResponse.de(mesas.save(m), null);
    }

    @Transactional
    public MesaResponse editarMesa(Long id, MesaRequest req) {
        Mesa m = mesa(id);
        boolean estaOcupada = ocupacion.ocupada(id);
        if (req.numero() != null && !req.numero().equals(m.getNumero())) {
            if (estaOcupada) throw ErrorNegocio.conflicto("La mesa " + m.getNumero() + " está ocupada; cambia el número cuando se libere");
            if (mesas.existsByNumeroAndIdNot(req.numero(), id)) throw ErrorNegocio.conflicto("Ya existe la mesa " + req.numero());
            m.setNumero(req.numero());
        }
        if (req.activa() != null && !req.activa() && m.isActiva() && estaOcupada) {
            throw ErrorNegocio.conflicto("La mesa tiene un pedido abierto; ciérralo antes de desactivarla");
        }
        m.setNombre(limpio(req.nombre()));
        m.setZona(limpio(req.zona()));
        if (req.capacidad() != null) m.setCapacidad(req.capacidad());
        if (req.orden() != null) m.setOrden(req.orden());
        if (req.activa() != null) m.setActiva(req.activa());
        return MesaResponse.de(m, ocupacion.mesasOcupadas().get(id));
    }

    @Transactional
    public void desactivarMesa(Long id) {
        Mesa m = mesa(id);
        if (ocupacion.ocupada(id)) {
            throw ErrorNegocio.conflicto("La mesa tiene un pedido abierto; ciérralo antes de desactivarla");
        }
        m.setActiva(false);
    }

    /** Guarda el orden de las mesas (las que no vengan en la lista conservan su orden, después). */
    @Transactional
    public List<MesaResponse> ordenarMesas(List<Long> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw ErrorNegocio.invalido("Hay mesas repetidas en la lista");
        Map<Long, Mesa> porId = new HashMap<>();
        mesas.findAllById(ids).forEach(m -> porId.put(m.getId(), m));
        if (porId.size() != ids.size()) throw ErrorNegocio.noEncontrado("Alguna de las mesas no existe");
        for (int i = 0; i < ids.size(); i++) porId.get(ids.get(i)).setOrden(i + 1);
        int siguiente = ids.size() + 1;
        for (Mesa m : mesas.findAllByOrderByOrdenAscNumeroAsc()) {
            if (!porId.containsKey(m.getId())) m.setOrden(siguiente++);
        }
        return listarMesas(true);
    }

    /**
     * Renumera 1, 2, 3… las mesas activas según su orden. Las inactivas quedan después,
     * para no chocar. No se permite con mesas ocupadas (el mesero vería cambiar los números).
     */
    @Transactional
    public List<MesaResponse> renumerarMesas() {
        if (!ocupacion.mesasOcupadas().isEmpty()) {
            throw ErrorNegocio.conflicto("Hay mesas ocupadas; renumera cuando todas estén libres");
        }
        List<Mesa> todas = new ArrayList<>(mesas.findAllByOrderByOrdenAscNumeroAsc());
        todas.sort(Comparator.comparing((Mesa m) -> !m.isActiva()));   // activas primero, sin perder el orden
        // Paso 1: números temporales negativos para no violar la regla de número único al intercambiar.
        todas.forEach(m -> m.setNumero(-m.getId().intValue()));
        mesas.flush();
        for (int i = 0; i < todas.size(); i++) {
            todas.get(i).setNumero(i + 1);
            todas.get(i).setOrden(i + 1);
        }
        mesas.flush();
        return listarMesas(true);
    }
}
