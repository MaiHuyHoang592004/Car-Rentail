package com.rentflow.common.exception;

import com.rentflow.common.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final CorrelationIdHelper correlationIdHelper;

    public GlobalExceptionHandler(CorrelationIdHelper correlationIdHelper) {
        this.correlationIdHelper = correlationIdHelper;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Validation error [{}]: {}", cid, errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Request validation failed", errors, cid));
    }

    @ExceptionHandler(com.rentflow.common.exception.AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleRentFlowAuthentication(
            com.rentflow.common.exception.AuthenticationException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Authentication error [{}]: {} - {}", cid, ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Authentication error [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("AUTH_INVALID_CREDENTIALS", "Authentication required", cid));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Access denied [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", "Access denied: insufficient permissions", cid));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Resource not found [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Business rule violation [{}]: {} - {}", cid, ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(ValidationException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Validation error [{}]: {} - {}", cid, ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(ConcurrencyException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Concurrency error [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(IdempotencyException.class)
    public ResponseEntity<ErrorResponse> handleIdempotency(IdempotencyException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Idempotency error [{}]: {} - {}", cid, ex.getCode(), ex.getMessage());
        HttpStatus status = "VALIDATION_ERROR".equals(ex.getCode()) ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT;
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePayment(PaymentException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.error("Payment error [{}]: {} - {}", cid, ex.getCode(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVehicleNotFound(VehicleNotFoundException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Vehicle not found [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(ListingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleListingNotFound(ListingNotFoundException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Listing not found [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(VehicleArchiveNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleVehicleArchiveNotAllowed(VehicleArchiveNotAllowedException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Vehicle archive not allowed [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), cid));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        String message = ex.getMessage() != null ? ex.getMessage() : "";
        if (message.contains("uq_listings_one_active_per_vehicle")) {
            log.warn("Data integrity violation [{}]: one active listing per vehicle", cid);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("ONE_ACTIVE_LISTING_PER_VEHICLE",
                            "Vehicle already has an active listing", cid));
        }
        if (message.contains("uq_driver_verification_active")) {
            log.warn("Data integrity violation [{}]: duplicate driver verification", cid);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("ALREADY_SUBMITTED", "Verification already submitted", cid));
        }
        log.error("Data integrity violation [{}]: {}", cid, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DATA_INTEGRITY_VIOLATION",
                        "Data integrity constraint was violated", cid));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.warn("Bad request [{}]: {}", cid, ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), cid));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request) {
        String cid = correlationIdHelper.getCorrelationId();
        log.error("Unexpected error [{}]: {}", cid, ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred", cid));
    }
}
