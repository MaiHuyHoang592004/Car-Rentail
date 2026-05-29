package com.rentflow.listing.controller;

import com.rentflow.common.security.UserPrincipal;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.common.web.PageableValidation;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.dto.UpdateListingRequest;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/host/listings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOST')")
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody com.rentflow.listing.dto.CreateListingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.createListing(request, principal.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<ListingSummaryResponse>> listListings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Sort sort,
            @AuthenticationPrincipal UserPrincipal principal) {

        Pageable pageable = PageableValidation.of(page, size, sort);

        ListingStatus statusEnum = null;
        if (status != null && !status.isBlank()) {
            try {
                statusEnum = ListingStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ValidationException("Invalid listing status: " + status);
            }
        }

        Page<ListingSummaryResponse> listings = listingService.listListings(
                principal.getUserId(), statusEnum, pageable);
        return ResponseEntity.ok(PageResponse.from(listings));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingResponse> getListing(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.getListing(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ListingResponse> updateListing(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateListingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.updateListing(id, principal.getUserId(), request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<ListingResponse> submitListing(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.submitListing(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<ListingResponse> archiveListing(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.archiveListing(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ListingResponse> reactivateListing(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        var response = listingService.reactivateListing(id, principal.getUserId());
        return ResponseEntity.ok(response);
    }
}
