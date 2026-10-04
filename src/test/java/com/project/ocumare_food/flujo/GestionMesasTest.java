package com.project.ocumare_food.flujo;

import com.project.ocumare_food.model.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** El gerente organiza el salón: zonas, orden, renumerar, desactivar. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GestionMesasTest extends ApiTest {

    String gerente, mesero;
    long m1, m2, m3, m4;

    @BeforeEach
    void salon() throws Exception {
        gerente = crearYEntrar("gerente-mesas", Rol.GERENTE);
        mesero = crearYEntrar("mesero-mesas", Rol.MESERO);
        m1 = id(post(gerente, "/api/mesas", "{\"zona\":\"Salón\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value(1)));                       // número automático
        m2 = id(post(gerente, "/api/mesas", "{\"zona\":\"Salón\"}").andExpect(jsonPath("$.numero").value(2)));
        m3 = id(post(gerente, "/api/mesas", "{\"zona\":\"Terraza\",\"capacidad\":6}").andExpect(jsonPath("$.numero").value(3)));
        m4 = id(post(gerente, "/api/mesas", "{\"numero\":10,\"nombre\":\"Barra\",\"zona\":\"Barra\"}"));
    }

    @Test
    void ordenarYRenumerarTrasQuitarUnaMesa() throws Exception {
        // Se retira la mesa 2
        delete(gerente, "/api/mesas/" + m2).andExpect(status().isNoContent());
        get(mesero, "/api/mesas").andExpect(jsonPath("$.length()").value(3));

        // Se reacomoda: Terraza primero
        put(gerente, "/api/mesas/orden", "{\"ids\":[" + m3 + "," + m1 + "," + m4 + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) m3))
                .andExpect(jsonPath("$[3].id").value((int) m2));     // la inactiva queda al final

        // Renumerar: activas 1..3 en ese orden, la inactiva después
        post(gerente, "/api/mesas/renumerar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value((int) m3)).andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[1].id").value((int) m1)).andExpect(jsonPath("$[1].numero").value(2))
                .andExpect(jsonPath("$[2].id").value((int) m4)).andExpect(jsonPath("$[2].numero").value(3))
                .andExpect(jsonPath("$[3].activa").value(false)).andExpect(jsonPath("$[3].numero").value(4));

        // Reactivar la mesa retirada
        put(gerente, "/api/mesas/" + m2, "{\"activa\":true}").andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void unaMesaOcupadaNoSeRenumeraNiSeDesactiva() throws Exception {
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Bebidas\"}"));
        long refresco = id(post(gerente, "/api/productos", "{\"nombre\":\"Refresco\",\"precioUsd\":1,\"categoriaId\":" + cat + "}"));
        post(gerente, "/api/turnos/abrir", "{\"tipo\":\"DIA\"}").andExpect(status().isCreated());
        post(mesero, "/api/pedidos", "{\"tipo\":\"MESA\",\"mesaId\":" + m1 + ",\"items\":[{\"productoId\":" + refresco + ",\"cantidad\":1}]}")
                .andExpect(status().isCreated());

        post(gerente, "/api/mesas/renumerar", null).andExpect(status().isConflict());
        delete(gerente, "/api/mesas/" + m1).andExpect(status().isConflict());
        put(gerente, "/api/mesas/" + m1, "{\"activa\":false}").andExpect(status().isConflict());
        put(gerente, "/api/mesas/" + m1, "{\"numero\":20}").andExpect(status().isConflict());
        // Sí se puede cambiar nombre o zona aunque esté ocupada
        put(gerente, "/api/mesas/" + m1, "{\"nombre\":\"Ventana\",\"zona\":\"Salón\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventana"));
    }

    @Test
    void validacionesYPermisos() throws Exception {
        post(gerente, "/api/mesas", "{\"numero\":10}").andExpect(status().isConflict());           // repetida
        put(gerente, "/api/mesas/" + m1, "{\"numero\":3}").andExpect(status().isConflict());
        put(gerente, "/api/mesas/orden", "{\"ids\":[" + m1 + "," + m1 + "]}").andExpect(status().isBadRequest());
        put(gerente, "/api/mesas/orden", "{\"ids\":[99999]}").andExpect(status().isNotFound());

        post(mesero, "/api/mesas", "{}").andExpect(status().isForbidden());
        put(mesero, "/api/mesas/orden", "{\"ids\":[" + m1 + "]}").andExpect(status().isForbidden());
        post(mesero, "/api/mesas/renumerar", null).andExpect(status().isForbidden());
        get(mesero, "/api/mesas").andExpect(jsonPath("$[2].zona").value("Terraza"));
    }
}
