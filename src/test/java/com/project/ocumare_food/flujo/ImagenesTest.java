package com.project.ocumare_food.flujo;

import com.project.ocumare_food.model.enums.Rol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Subir fotos de productos y verlas desde la web sin iniciar sesión. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImagenesTest extends ApiTest {

    /** Un JPG mínimo: lo que importa es que empiece con la firma FF D8 FF. */
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};

    @Test
    void elGerenteSubeUnaFotoYCualquieraLaVe() throws Exception {
        String gerente = crearYEntrar("gerente-fotos", Rol.GERENTE);
        String json = mvc.perform(multipart("/api/imagenes").file(new MockMultipartFile("archivo", "plato.jpg", "image/jpeg", JPG))
                        .header("Authorization", gerente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").exists())
                .andReturn().getResponse().getContentAsString();
        String url = com.jayway.jsonpath.JsonPath.read(json, "$.url");

        get(null, url).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(JPG))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")));

        // La foto queda asociada al producto y sale en el menú público
        long cat = id(post(gerente, "/api/categorias", "{\"nombre\":\"Con foto\"}"));
        post(gerente, "/api/productos", "{\"nombre\":\"Plato con foto\",\"precioUsd\":3,\"categoriaId\":" + cat + ",\"imagenUrl\":\"" + url + "\"}")
                .andExpect(jsonPath("$.imagenUrl").value(url));
    }

    @Test
    void rechazaArchivosQueNoSonFotosYAlMesero() throws Exception {
        String gerente = crearYEntrar("gerente-fotos2", Rol.GERENTE);
        mvc.perform(multipart("/api/imagenes").file(new MockMultipartFile("archivo", "virus.jpg", "image/jpeg", "<script>alert(1)</script>".getBytes()))
                        .header("Authorization", gerente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El archivo no es una foto JPG, PNG o WebP"));

        String mesero = crearYEntrar("mesero-fotos", Rol.MESERO);
        mvc.perform(multipart("/api/imagenes").file(new MockMultipartFile("archivo", "plato.jpg", "image/jpeg", JPG))
                        .header("Authorization", mesero))
                .andExpect(status().isForbidden());
        get(null, "/api/imagenes/999999").andExpect(status().isNotFound());
    }
}
