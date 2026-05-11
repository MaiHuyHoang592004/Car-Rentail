package com.rentflow.listing.controller;

import com.rentflow.listing.dto.ListingDetailResponse;
import com.rentflow.listing.service.ListingSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingDetailController {

    private final ListingSearchService listingSearchService;

    @GetMapping("/{id}")
    public ResponseEntity<ListingDetailResponse> getListingDetail(@PathVariable UUID id) {
        ListingDetailResponse response = listingSearchService.getListingDetail(id);
        return ResponseEntity.ok(response);
    }
}
