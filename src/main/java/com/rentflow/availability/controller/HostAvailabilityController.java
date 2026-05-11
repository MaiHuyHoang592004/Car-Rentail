package com.rentflow.availability.controller;

import com.rentflow.availability.dto.AvailabilityUpdateResponse;
import com.rentflow.availability.dto.DateListRequest;
import com.rentflow.availability.dto.HostAvailabilityResponse;
import com.rentflow.availability.service.AvailabilityService;
import com.rentflow.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/host/listings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOST')")
public class HostAvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping("/{id}/availability")
    public ResponseEntity<HostAvailabilityResponse> getHostAvailability(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.checkOwnership(id, principal.getUserId());
        return ResponseEntity.ok(availabilityService.getHostAvailability(id, from, to, principal.getUserId()));
    }

    @PostMapping("/{id}/availability/block")
    public ResponseEntity<AvailabilityUpdateResponse> blockDates(
            @PathVariable UUID id,
            @RequestBody DateListRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.checkOwnership(id, principal.getUserId());
        int count = availabilityService.blockDates(id, request.dates(), principal.getUserId());
        return ResponseEntity.ok(new AvailabilityUpdateResponse(count));
    }

    @PostMapping("/{id}/availability/unblock")
    public ResponseEntity<AvailabilityUpdateResponse> unblockDates(
            @PathVariable UUID id,
            @RequestBody DateListRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        availabilityService.checkOwnership(id, principal.getUserId());
        int count = availabilityService.unblockDates(id, request.dates(), principal.getUserId());
        return ResponseEntity.ok(new AvailabilityUpdateResponse(count));
    }
}
