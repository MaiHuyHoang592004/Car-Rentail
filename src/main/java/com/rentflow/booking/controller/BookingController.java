package com.rentflow.booking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingService;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.booking.service.CancelBookingRequest;
import com.rentflow.booking.service.CancelBookingResponse;
import com.rentflow.booking.service.CreateBookingRequest;
import com.rentflow.booking.service.PatchBookingLocationRequest;
import com.rentflow.common.exception.IdempotencyException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private static final Pattern UUID_V4_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$");

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateBookingRequest request) {
        validateIdempotencyKey(idempotencyKey);
        BookingResponse response = bookingService.createBooking(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<BookingSummaryResponse>> listMyBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        BookingStatus statusFilter = parseStatus(status);
        return ResponseEntity.ok(bookingService.listMyBookings(statusFilter, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable UUID id) {
        return ResponseEntity.ok(bookingService.getBooking(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BookingResponse> patchBookingLocations(
            @PathVariable UUID id,
            @RequestBody JsonNode requestBody) {
        PatchBookingLocationRequest request = parsePatchRequest(requestBody);
        return ResponseEntity.ok(bookingService.patchBookingLocations(id, request));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CancelBookingRequest request) {
        validateIdempotencyKey(idempotencyKey);
        CancelBookingRequest cancelRequest = request == null ? new CancelBookingRequest(null) : request;
        return ResponseEntity.ok(bookingService.cancelBooking(id, idempotencyKey, cancelRequest));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required");
        }
        if (!UUID_V4_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ValidationException("Idempotency-Key must be a UUID-v4 value");
        }
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

    private PatchBookingLocationRequest parsePatchRequest(JsonNode requestBody) {
        if (requestBody == null || !requestBody.isObject()) {
            throw new ValidationException("Booking patch body must be a JSON object");
        }

        String pickupLocation = null;
        String returnLocation = null;
        boolean hasNonNullAllowedField = false;
        Iterator<String> fieldNames = requestBody.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode value = requestBody.get(fieldName);
            switch (fieldName) {
                case "pickupLocation" -> {
                    pickupLocation = readOptionalLocation(value, fieldName);
                    hasNonNullAllowedField = hasNonNullAllowedField || pickupLocation != null;
                }
                case "returnLocation" -> {
                    returnLocation = readOptionalLocation(value, fieldName);
                    hasNonNullAllowedField = hasNonNullAllowedField || returnLocation != null;
                }
                default -> throw new ValidationException("Unknown booking patch field: " + fieldName);
            }
        }

        if (!hasNonNullAllowedField) {
            throw new ValidationException("At least one location field must be provided");
        }
        return new PatchBookingLocationRequest(pickupLocation, returnLocation);
    }

    private String readOptionalLocation(JsonNode value, String fieldName) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new ValidationException(fieldName + " must be a string");
        }
        return value.asText();
    }
}
