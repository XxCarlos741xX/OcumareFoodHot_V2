package com.project.ocumare_food.compartido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Convierte las excepciones en respuestas JSON uniformes: { "error": ..., "mensaje": ..., "campos": {...} } */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    public record RespuestaError(int status, String error, String mensaje, Map<String, String> campos) {
    }

    private static ResponseEntity<RespuestaError> respuesta(HttpStatus status, String mensaje, Map<String, String> campos) {
        return ResponseEntity.status(status)
                .body(new RespuestaError(status.value(), status.getReasonPhrase(), mensaje, campos));
    }

    @ExceptionHandler(ErrorNegocio.class)
    ResponseEntity<RespuestaError> negocio(ErrorNegocio e) {
        return respuesta(e.getStatus(), e.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos", campos);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<RespuestaError> malFormado(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "La petición no tiene el formato esperado", null);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<RespuestaError> validacionParametros(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Hay datos inválidos", null);
    }

    /** Choque con una regla única de la base (dos operaciones iguales al mismo tiempo, por ejemplo). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<RespuestaError> integridad(DataIntegrityViolationException e) {
        log.warn("Conflicto de integridad: {}", e.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, "La operación choca con datos existentes; recarga e intenta de nuevo", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<RespuestaError> archivoGrande(Exception e) {
        return respuesta(HttpStatus.PAYLOAD_TOO_LARGE, "La foto es demasiado pesada (máximo 2 MB)", null);
    }

    @ExceptionHandler(org.springframework.web.multipart.support.MissingServletRequestPartException.class)
    ResponseEntity<RespuestaError> faltaArchivo(Exception e) {
        return respuesta(HttpStatus.BAD_REQUEST, "No llegó ninguna foto", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<RespuestaError> prohibido(AccessDeniedException e) {
        return respuesta(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<RespuestaError> inesperado(Exception e) {
        // Errores propios de Spring (404 de ruta, 405 método no permitido…) conservan su código.
        if (e instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            return respuesta(status, status.getReasonPhrase(), null);
        }
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", null);
    }
}
