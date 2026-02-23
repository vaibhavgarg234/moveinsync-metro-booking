package com.moveinsync.metro.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, Object message, LocalDateTime timestamp) {}

    @ExceptionHandler(MetroException.StopNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(RuntimeException ex) {
        return build(404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(MetroException.RouteNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleRouteNotFound(RuntimeException ex) {
        return build(404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(MetroException.BookingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleBookingNotFound(RuntimeException ex) {
        return build(404, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(MetroException.DuplicateResourceException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicate(RuntimeException ex) {
        return build(409, "Conflict", ex.getMessage());
    }

    @ExceptionHandler({MetroException.NoPathException.class,
                       MetroException.InvalidBookingStateException.class})
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleUnprocessable(RuntimeException ex) {
        return build(422, "Unprocessable Entity", ex.getMessage());
    }

    @ExceptionHandler(MetroException.GraphNotReadyException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleGraphNotReady(RuntimeException ex) {
        return build(503, "Service Unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : err.getObjectName();
            errors.put(field, err.getDefaultMessage());
        });
        return build(400, "Validation Failed", errors);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return build(500, "Internal Server Error", ex.getMessage());
    }

    private ErrorResponse build(int status, String error, Object message) {
        return new ErrorResponse(status, error, message, LocalDateTime.now());
    }
}