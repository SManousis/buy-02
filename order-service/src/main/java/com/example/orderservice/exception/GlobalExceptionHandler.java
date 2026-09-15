package com.example.orderservice.exception;

import com.example.orderservice.config.CorrelationIdFilter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
        Map<String,String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> details.put(e.getField(), e.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<Map<String,Object>> malformed() { return build(HttpStatus.BAD_REQUEST, "Malformed request body", null); }
    @ExceptionHandler(NotFoundException.class) ResponseEntity<Map<String,Object>> missing(NotFoundException ex) { return build(HttpStatus.NOT_FOUND, ex.getMessage(), null); }
    @ExceptionHandler(CartConflictException.class) ResponseEntity<Map<String,Object>> conflict(CartConflictException ex) { return build(HttpStatus.CONFLICT, ex.getMessage(), null); }
    @ExceptionHandler(ProductServiceUnavailableException.class) ResponseEntity<Map<String,Object>> unavailable(ProductServiceUnavailableException ex) { return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), null); }
    private ResponseEntity<Map<String,Object>> build(HttpStatus status, String message, Object details) {
        Map<String,Object> body = new LinkedHashMap<>(); body.put("timestamp", Instant.now().toString()); body.put("status", status.value()); body.put("error", status.getReasonPhrase()); body.put("message", message); body.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY)); if(details != null) body.put("details", details); return ResponseEntity.status(status).body(body);
    }
}
