package com.rentflow.listing.controller;

import com.rentflow.listing.dto.ListingSearchRequest;
import com.rentflow.listing.dto.ListingSearchResponse;
import com.rentflow.listing.service.ListingSearchService;
import com.rentflow.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
@RequiredArgsConstructor
public class ListingSearchController {

    private final ListingSearchService listingSearchService;

    @GetMapping
    public ResponseEntity<PageResponse<ListingSearchResponse>> search(ListingSearchRequest request) {
        PageResponse<ListingSearchResponse> response = listingSearchService.search(request);
        return ResponseEntity.ok(response);
    }
}
