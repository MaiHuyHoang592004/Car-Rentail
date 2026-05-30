package com.rentflow.listing.controller;

import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.dto.AdminListingDetailResponse;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.service.AdminListingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/listings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminListingController {

    private final AdminListingService adminListingService;

    @GetMapping
    public ResponseEntity<PageResponse<ListingSummaryResponse>> listListings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID hostId,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort) {

        Pageable pageable = PageableValidation.of(page, size, sort);

        ListingStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ListingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid listing status: " + status);
            }
        }

        Page<ListingSummaryResponse> listings = adminListingService.listListings(
                statusEnum, hostId, city, pageable);
        return ResponseEntity.ok(PageResponse.from(listings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminListingDetailResponse> getListing(@PathVariable UUID id) {
        var response = adminListingService.getListing(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ListingResponse> approveListing(@PathVariable UUID id) {
        var response = adminListingService.approveListing(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ListingResponse> rejectListing(
            @PathVariable UUID id,
            @RequestBody RejectRequest request) {
        var response = adminListingService.rejectListing(id, request.reason());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ListingResponse> suspendListing(
            @PathVariable UUID id,
            @RequestBody SuspendRequest request) {
        var response = adminListingService.suspendListing(id, request.reason(), request.source(), request.suspensionUntil());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ListingResponse> reactivateListing(@PathVariable UUID id) {
        var response = adminListingService.reactivateListing(id);
        return ResponseEntity.ok(response);
    }

    public record RejectRequest(String reason) {}
    public record SuspendRequest(String reason, String source, Instant suspensionUntil) {}
}
