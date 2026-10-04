package com.project.ocumare_food.controller;

import com.project.ocumare_food.exception.ErrorNegocio;
import com.project.ocumare_food.model.Imagen;
import com.project.ocumare_food.repository.ImagenRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;

/**
 * Fotos de productos.
 *  - POST /api/imagenes (GERENTE/ADMIN): sube una foto JPG, PNG o WebP de hasta 2 MB → { id, url }.
 *  - GET  /api/imagenes/{id} (público): la sirve con caché larga (una foto nunca cambia: se sube otra).
 */
@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    static final int MAX_BYTES = 2 * 1024 * 1024;

    private final ImagenRepository imagenes;

    public ImagenController(ImagenRepository imagenes) {
        this.imagenes = imagenes;
    }

    public record ImagenSubida(Long id, String url) {
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ImagenSubida subir(@RequestParam("archivo") MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) throw ErrorNegocio.invalido("No llegó ninguna foto");
        if (archivo.getSize() > MAX_BYTES) throw ErrorNegocio.invalido("La foto pesa más de 2 MB");
        byte[] datos = archivo.getBytes();
        String tipo = tipoReal(datos);
        if (tipo == null) throw ErrorNegocio.invalido("El archivo no es una foto JPG, PNG o WebP");
        Imagen img = imagenes.save(Imagen.builder().tipoContenido(tipo).datos(datos).tamano(datos.length).build());
        return new ImagenSubida(img.getId(), "/api/imagenes/" + img.getId());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> ver(@PathVariable Long id) {
        Imagen img = imagenes.findById(id).orElseThrow(() -> ErrorNegocio.noEncontrado("Foto no encontrada"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getTipoContenido()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(img.getDatos());
    }

    /** Se fija en los primeros bytes del archivo, no en el nombre ni en lo que diga el navegador. */
    static String tipoReal(byte[] b) {
        if (b.length < 12) return null;
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P')
            return "image/webp";
        return null;
    }

}
