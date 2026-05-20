package com.biffis.tracker.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Uniform error shape: {"error": "<code>", "message": "..."} per docs/API.md.
 * Never echoes user-supplied content (no PII in error bodies).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound() {
        return body(HttpStatus.NOT_FOUND, "not_found", null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> forbidden() {
        return body(HttpStatus.FORBIDDEN, "forbidden", null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> conflict() {
        return body(HttpStatus.CONFLICT, "conflict", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        Map<String, Object> b = new HashMap<>();
        b.put("error", "validation_failed");
        b.put("fieldErrors", fieldErrors);
        return ResponseEntity.unprocessableEntity().body(b);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        Map<String, Object> b = new HashMap<>();
        b.put("error", code);
        if (message != null) {
            b.put("message", message);
        }
        return ResponseEntity.status(status).body(b);
    }
}
