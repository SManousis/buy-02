package com.example.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import com.example.userservice.config.CorrelationIdFilter;

/**
 * Centralised exception → HTTP response mapping.
 *
 * Rules:
 *  - 4xx: log at WARN (expected, client-side error)
 *  - 5xx: log at ERROR (unexpected, needs investigation)
 *  - Never expose internal stack traces or raw exception messages to callers
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 400 — bean validation failures (e.g. @NotBlank, @Email)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first));
        log.warn("Validation failed: {}", fieldErrors);
        return error(HttpStatus.BAD_REQUEST, "Validation failed", fieldErrors);
    }

    // 400 — malformed JSON body (e.g. invalid enum value for UserRole)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleUnreadable(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "Malformed request body", null);
    }

    // 401 — wrong credentials at login
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials attempt");
        return error(HttpStatus.UNAUTHORIZED, "Invalid email or password", null);
    }

    // 401 — expired or tampered JWT (hit when manually calling validateToken)
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Object> handleJwt(JwtException ex) {
        log.warn("JWT error: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "Invalid or expired token", null);
    }

    // 403 — authenticated but insufficient role
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "Access denied", null);
    }

    // 404 — resource not found
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        log.warn("Not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    // 409 — duplicate email / username
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Object> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ex.getMessage(), null);
    }

    // 409 — MongoDB unique-index violation that occurs during save
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Object> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("Duplicate key error: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "Email or username already in use", null);
    }

    // 500 — catch-all: log full stack trace internally but return a safe message
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", null);
    }

    @ExceptionHandler(InvalidAvatarMediaException.class)
    public ResponseEntity<Object> handleInvalidAvatar(
            InvalidAvatarMediaException ex
    ) {
        log.warn("Invalid avatar media reference");
        return error(
                HttpStatus.BAD_REQUEST,
                "Invalid avatar media reference",
                null
        );
    }

    @ExceptionHandler(MediaServiceUnavailableException.class)
    public ResponseEntity<Object> handleMediaUnavailable(
            MediaServiceUnavailableException ex
    ) {
        log.warn("Media validation unavailable: {}", ex.getMessage());
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Media validation is temporarily unavailable",
                null
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<Object> error(HttpStatus status, String message, Object details) {
        var body = new LinkedHashMap<String, Object>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));
        if (details != null) body.put("details", details);
        return ResponseEntity.status(status).body(body);
    }
}
