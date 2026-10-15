package com.roadassist.backend.controller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
    AssistanceRequestController.class,
    MechanicRequestController.class,
    MechanicAvailabilityController.class,
    FeedbackController.class,
    AdminController.class,
    MechanicProfileController.class
})
public class AssistanceRequestExceptionHandler {

    public record ErrorResponse(int status, String message) {
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalidInput(Exception exception) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request input");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> constraintViolation(DataIntegrityViolationException exception) {
        return response(HttpStatus.CONFLICT, "Request conflicts with existing data");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> requestFailure(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = status.value() == 404 ? "Resource not found"
            : status.value() == 403 ? "Access denied"
            : status.value() == 409 ? "Request conflicts with existing data"
            : status.value() == 400 ? "Invalid request input"
            : status.value() == 422 || status.value() == 503
                ? exception.getReason()
            : status.is4xxClientError() ? "Invalid request" : "Request could not be processed";
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> unexpectedFailure(Exception exception) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Request could not be processed");
    }

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(status.value(), message));
    }
}
