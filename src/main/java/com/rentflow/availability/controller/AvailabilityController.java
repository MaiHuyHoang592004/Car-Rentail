package com.rentflow.availability.controller;

import com.rentflow.availability.dto.PublicAvailabilityResponse;
import com.rentflow.availability.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/{listingId}/availability")
    public ResponseEntity<PublicAvailabilityResponse> getPublicAvailability(
            @PathVariable UUID listingId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        PublicAvailabilityResponse response = availabilityService.getPublicAvailability(listingId, from, to);
        return ResponseEntity.ok(response);
    }
}
