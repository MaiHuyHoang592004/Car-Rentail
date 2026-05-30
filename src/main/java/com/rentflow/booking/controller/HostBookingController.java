package com.rentflow.booking.controller;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.booking.service.HostBookingApprovalService;
import com.rentflow.common.exception.IdempotencyKeyRequiredException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/host/bookings")
@RequiredArgsConstructor
public class HostBookingController {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final HostBookingApprovalService hostBookingApprovalService;

    @GetMapping
    public ResponseEntity<PageResponse<BookingSummaryResponse>> listHostBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        BookingStatus statusFilter = parseStatus(status);
        return ResponseEntity.ok(hostBookingApprovalService.listHostBookings(statusFilter, listingId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getHostBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(hostBookingApprovalService.getHostBooking(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<BookingResponse> approveBooking(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(hostBookingApprovalService.approveBooking(id, idempotencyKey));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<BookingResponse> rejectBooking(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) RejectBookingRequest request) {
        validateIdempotencyKey(idempotencyKey);
        return ResponseEntity.ok(hostBookingApprovalService.rejectBooking(id, idempotencyKey, validateRejectReason(request)));
    }

    private BookingStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return BookingStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Invalid booking status: " + status);
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be a UUID-v4 value");
        }
    }

    private String validateRejectReason(RejectBookingRequest request) {
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ValidationException("reason is required");
        }
        String trimmed = request.reason().trim();
        if (trimmed.length() > 500) {
            throw new ValidationException("reason must be at most 500 characters");
        }
        return trimmed;
    }

    public record RejectBookingRequest(String reason) {
    }
}
