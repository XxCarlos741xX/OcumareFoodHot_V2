package com.project.ocumare_food.usuarios.config;

import com.project.ocumare_food.usuarios.model.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * En MySQL, Hibernate crea la columna usuarios.rol como ENUM('MESERO','GERENTE','ADMIN').
 * Si la base se creó antes de que existiera el rol CAJERO, esa columna no lo acepta y
 * "ddl-auto=update" no la cambia. Al arrancar, si al ENUM le falta algún rol, se le agrega.
 * No toca los datos, solo corre en MySQL y, una vez actualizada la columna, ya no hace nada.
 */
@Component
@Order(0)
public class ActualizarColumnaRol implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ActualizarColumnaRol.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public ActualizarColumnaRol(DataSource dataSource) {
        this.dataSource = dataSource;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String motor;
            try (Connection c = dataSource.getConnection()) {
                motor = c.getMetaData().getDatabaseProductName();
            }
            if (motor == null || !motor.toLowerCase().contains("mysql")) return;
            List<String> tipos = jdbc.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'rol'",
                    String.class);
            if (tipos.isEmpty() || !tipos.get(0).toLowerCase().startsWith("enum")) return;
            String actual = tipos.get(0);
            boolean faltaAlguno = Arrays.stream(Rol.values()).anyMatch(r -> !actual.contains("'" + r.name() + "'"));
            if (!faltaAlguno) return;
            String valores = Arrays.stream(Rol.values()).map(r -> "'" + r.name() + "'").collect(Collectors.joining(","));
            jdbc.execute("ALTER TABLE usuarios MODIFY rol ENUM(" + valores + ") NOT NULL");
            log.info("La columna usuarios.rol ahora acepta los roles {}", valores);
        } catch (Exception e) {
            log.warn("No se pudo revisar la columna usuarios.rol: {}", e.getMessage());
        }
    }
}
