package com.project.ocumare_food.cocina.controller;

import com.project.ocumare_food.cocina.CocinaService;
import com.project.ocumare_food.cocina.FormatoComanda;
import com.project.ocumare_food.cocina.model.Comanda;
import com.project.ocumare_food.cocina.model.Impresora;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rutas que usan las impresoras (no el personal). No llevan sesión: se identifican con el token secreto
 * de la impresora, que va en la dirección. Un token falso o desactivado responde 404.
 *
 * 1) Impresora con nube, protocolo Star CloudPRNT (HTTP). En la impresora se configura como servidor:
 *      https://<dominio>/api/impresion/cloudprnt/<token>
 *    - POST: la impresora pregunta si hay trabajo; respondemos jobReady y el tipo de contenido (text/plain).
 *    - GET:  descarga la hoja (texto plano).
 *    - DELETE ?code=200%20OK: confirma que la imprimió (otro código = falló, se reintenta).
 *
 * 2) Puente para impresoras de red comunes (ESC/POS por el puerto 9100), ver deploy/puente-impresora:
 *    - GET  /api/impresion/puente/<token>/siguiente → 200 con la hoja (texto con marcas de letra grande)
 *      y la cabecera X-Comanda-Id, o 204 si no hay nada.
 *    - POST /api/impresion/puente/<token>/<id>?ok=true|false → confirma.
 */
@RestController
@RequestMapping("/api/impresion")
public class ImpresionController {

    private static final MediaType TEXTO = new MediaType("text", "plain", StandardCharsets.US_ASCII);

    private final CocinaService cocina;

    public ImpresionController(CocinaService cocina) {
        this.cocina = cocina;
    }

    // ---------------- CloudPRNT ----------------

    @PostMapping("/cloudprnt/{token}")
    public Map<String, Object> cloudPrntConsulta(@PathVariable String token,
                                                 @RequestBody(required = false) Map<String, Object> estado) {
        Object codigo = estado == null ? null : estado.get("statusCode");
        cocina.conectar(token, "CLOUDPRNT", codigo == null ? null : URLDecoder.decode(String.valueOf(codigo), StandardCharsets.UTF_8));
        Optional<Comanda> siguiente = cocina.siguiente();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("jobReady", siguiente.isPresent());
        if (siguiente.isPresent()) {
            r.put("mediaTypes", List.of("text/plain"));
            r.put("jobToken", String.valueOf(siguiente.get().getId()));
        }
        return r;
    }

    @GetMapping("/cloudprnt/{token}")
    public ResponseEntity<byte[]> cloudPrntHoja(@PathVariable String token,
                                                @RequestParam(name = "token", required = false) String trabajo) {
        Impresora i = cocina.conectar(token, "CLOUDPRNT", null);
        Optional<Comanda> c = cocina.siguiente();
        if (c.isEmpty() || (trabajo != null && !trabajo.equals(String.valueOf(c.get().getId())))) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(TEXTO)
                .body(FormatoComanda.texto(c.get(), i.getColumnas(), false).getBytes(StandardCharsets.US_ASCII));
    }

    @DeleteMapping("/cloudprnt/{token}")
    public ResponseEntity<Void> cloudPrntConfirmar(@PathVariable String token,
                                                   @RequestParam(required = false) String code,
                                                   @RequestParam(name = "token", required = false) String trabajo) {
        Impresora i = cocina.conectar(token, "CLOUDPRNT", code);
        Long id = numero(trabajo);
        if (id == null) id = cocina.siguiente().map(Comanda::getId).orElse(null);
        if (id != null) cocina.confirmar(id, i, code == null || code.trim().startsWith("2"));
        return ResponseEntity.ok().build();
    }

    // ---------------- Puente ----------------

    @GetMapping("/puente/{token}/siguiente")
    public ResponseEntity<byte[]> puenteSiguiente(@PathVariable String token) {
        Impresora i = cocina.conectar(token, "PUENTE", null);
        return cocina.siguiente()
                .map(c -> ResponseEntity.ok().contentType(TEXTO)
                        .header("X-Comanda-Id", String.valueOf(c.getId()))
                        .header("X-Columnas", String.valueOf(i.getColumnas()))
                        .body(FormatoComanda.texto(c, i.getColumnas(), true).getBytes(StandardCharsets.US_ASCII)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/puente/{token}/{id}")
    public ResponseEntity<Void> puenteConfirmar(@PathVariable String token, @PathVariable Long id,
                                                @RequestParam(defaultValue = "true") boolean ok,
                                                @RequestParam(required = false) String estado) {
        Impresora i = cocina.conectar(token, "PUENTE", estado);
        cocina.confirmar(id, i, ok);
        return ResponseEntity.noContent().build();
    }

    private static Long numero(String s) {
        try {
            return s == null ? null : Long.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
