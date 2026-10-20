package com.restaurant.config;

import com.restaurant.exception.AuthenticationException;
import com.restaurant.exception.BadRequestException;
import com.restaurant.exception.ConflictException;
import com.restaurant.exception.EmptyOrderException;
import com.restaurant.exception.ForbiddenException;
import com.restaurant.exception.InvalidOrderStateException;
import com.restaurant.exception.ItemUnavailableException;
import com.restaurant.exception.OrderNotFoundException;
import com.restaurant.exception.RestaurantException;
import com.restaurant.exception.SessionNotFoundException;
import com.restaurant.exception.StatusConflictException;
import com.restaurant.exception.TableNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * // OOP: Polymorphism – one base handler catches the whole RestaurantException hierarchy.
 * Maps every domain/validation error to the docs/openapi.yaml error body:
 *   { error, message, statusCode, timestamp }  (+ details, + expectedStatus/actualStatus)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- helpers ----------

    /** docs/openapi.yaml: timestamp is an ISO-8601 UTC string, seconds precision. */
    private static final DateTimeFormatter ERROR_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private static String now() {
        return ERROR_TIMESTAMP.format(Instant.now());
    }

    /** Builds the canonical error payload used by every handler below. */
    private ResponseEntity<Map<String, Object>> error(String code, String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", message);
        body.put("statusCode", status.value());
        body.put("timestamp", now());
        return ResponseEntity.status(status).body(body);
    }

    // ---------- 404 Not Found ----------

    @ExceptionHandler(TableNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTableNotFound(TableNotFoundException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSessionNotFound(SessionNotFoundException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // ---------- 401 Unauthorized / 403 Forbidden ----------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    // ---------- 409 Conflict ----------

    /** Optimistic-concurrency clash – carries expected/actual statuses (StatusConflictErrorResponse). */
    @ExceptionHandler(StatusConflictException.class)
    public ResponseEntity<Map<String, Object>> handleStatusConflict(StatusConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("statusCode", HttpStatus.CONFLICT.value());
        body.put("timestamp", now());
        body.put("expectedStatus", ex.getExpected() != null ? ex.getExpected().name() : null);
        body.put("actualStatus", ex.getActual() != null ? ex.getActual().name() : null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("DB integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return error("CONFLICT", "The request conflicts with existing data (duplicate or referenced value).",
                HttpStatus.CONFLICT);
    }

    // ---------- 400 Bad Request ----------

    /** Sold-out / discontinued item (contract: 400 on POST /api/orders). */
    @ExceptionHandler(ItemUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleItemUnavailable(ItemUnavailableException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmptyOrderException.class)
    public ResponseEntity<Map<String, Object>> handleEmptyOrder(EmptyOrderException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(InvalidOrderStateException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    /** @Valid failures on @RequestBody records → 400 with a per-field details list. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> "Field '" + f.getField() + "' " + f.getDefaultMessage())
                .toList();
        ResponseEntity<Map<String, Object>> response = error("VALIDATION_FAILED",
                "Invalid request payload.", HttpStatus.BAD_REQUEST);
        response.getBody().put("details", details);
        return response;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .toList();
        ResponseEntity<Map<String, Object>> response = error("VALIDATION_FAILED",
                "Invalid request parameters.", HttpStatus.BAD_REQUEST);
        response.getBody().put("details", details);
        return response;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error("BAD_REQUEST", "Malformed JSON body or invalid enum value.", HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return error("BAD_REQUEST", "Missing required parameter: " + ex.getParameterName(),
                HttpStatus.BAD_REQUEST);
    }

    // ---------- 404 for unknown URLs ----------

    /**
     * A missing static resource / unknown path must stay a 404. Without this handler the
     * catch-all {@code Exception} handler below would turn every typo in a URL into a 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return error("NOT_FOUND", "No resource found for " + ex.getResourcePath() + ".", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error("BAD_REQUEST",
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.",
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = ex.getMethod() != null
                ? "HTTP method " + ex.getMethod() + " is not supported for this endpoint."
                : ex.getMessage();
        return error("METHOD_NOT_ALLOWED", message, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return error("UNSUPPORTED_MEDIA_TYPE", "Unsupported Content-Type. Please send application/json.",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    // ---------- 500 fallbacks ----------

    @ExceptionHandler(RestaurantException.class)
    public ResponseEntity<Map<String, Object>> handleRestaurantException(RestaurantException ex) {
        log.error("Unmapped domain exception [{}]: {}", ex.getErrorCode(), ex.getMessage());
        return error(ex.getErrorCode(), ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error("INTERNAL_ERROR", "An unexpected error occurred.", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
