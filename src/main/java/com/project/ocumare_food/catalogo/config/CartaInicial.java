package com.project.ocumare_food.catalogo.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.project.ocumare_food.catalogo.model.Categoria;
import com.project.ocumare_food.catalogo.model.Producto;
import com.project.ocumare_food.catalogo.repository.CategoriaRepository;
import com.project.ocumare_food.catalogo.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La primera vez que arranca (sin ninguna categoría), carga la carta real del local
 * desde carta-inicial.json (sacada del menú de la web y de Instagram).
 * Después la carta se administra en Gestión, Carta; este archivo ya no se vuelve a leer.
 */
@Component
@Order(2)
public class CartaInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CartaInicial.class);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Plato(String categoria, String nombre, String descripcion, BigDecimal precioUsd, String imagen, boolean destacado) {
    }

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final boolean habilitada;

    public CartaInicial(CategoriaRepository categorias, ProductoRepository productos,
                        @Value("${ocumare.carta-inicial:true}") boolean habilitada) {
        this.categorias = categorias;
        this.productos = productos;
        this.habilitada = habilitada;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (!habilitada || categorias.count() > 0) return;
        ClassPathResource archivo = new ClassPathResource("carta-inicial.json");
        if (!archivo.exists()) return;
        List<Plato> platos;
        try (InputStream in = archivo.getInputStream()) {
            platos = List.of(JsonMapper.builder().build().readValue(in, Plato[].class));
        }
        Map<String, Categoria> porNombre = new LinkedHashMap<>();
        for (Plato p : platos) {
            Categoria c = porNombre.computeIfAbsent(p.categoria(), n ->
                    categorias.save(Categoria.builder().nombre(n).orden(porNombre.size() + 1).build()));
            productos.save(Producto.builder()
                    .nombre(p.nombre())
                    .descripcion(p.descripcion())
                    .precioUsd(p.precioUsd())
                    .categoria(c)
                    .imagenUrl(p.imagen())
                    .destacado(p.destacado())
                    .build());
        }
        log.info("Carta inicial cargada: {} categorías y {} platos.", porNombre.size(), platos.size());
    }
}
