package com.project.ocumare_food;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifica la arquitectura de monolito modular:
 *  - no hay ciclos entre módulos,
 *  - cada módulo solo usa lo que los otros exponen (su paquete raíz o un @NamedInterface).
 * Si alguien usa, por ejemplo, el repositorio de pedidos desde caja, esta prueba falla.
 */
class ModulosTest {

    static final ApplicationModules MODULOS = ApplicationModules.of(OcumareFoodApplication.class);

    @Test
    void losModulosRespetanSusLimites() {
        MODULOS.verify();
    }

    /** Genera los diagramas de módulos en build/spring-modulith-docs (PlantUML). */
    @Test
    void documentarModulos() {
        new Documenter(MODULOS).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
