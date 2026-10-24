package com.project.ocumare_food.compartido;

import org.springframework.http.HttpStatus;

/** Error de una regla del negocio, con el código HTTP que debe recibir el cliente. */
public class ErrorNegocio extends RuntimeException {

    private final HttpStatus status;

    public ErrorNegocio(HttpStatus status, String mensaje) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ErrorNegocio noEncontrado(String mensaje) {
        return new ErrorNegocio(HttpStatus.NOT_FOUND, mensaje);
    }

    public static ErrorNegocio conflicto(String mensaje) {
        return new ErrorNegocio(HttpStatus.CONFLICT, mensaje);
    }

    public static ErrorNegocio prohibido(String mensaje) {
        return new ErrorNegocio(HttpStatus.FORBIDDEN, mensaje);
    }

    public static ErrorNegocio invalido(String mensaje) {
        return new ErrorNegocio(HttpStatus.BAD_REQUEST, mensaje);
    }
}
