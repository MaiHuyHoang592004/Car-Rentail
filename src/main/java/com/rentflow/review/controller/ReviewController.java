package com.rentflow.review.controller;

import com.rentflow.common.web.PageableValidation;
import com.rentflow.review.dto.CreateReviewRequest;
import com.rentflow.review.dto.ListingReviewPageResponse;
import com.rentflow.review.dto.ReviewResponse;
import com.rentflow.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/bookings/{id}/review")
    public ResponseEntity<ReviewResponse> createReview(
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.createReview(bookingId, request));
    }

    @GetMapping("/listings/{id}/reviews")
    public ResponseEntity<ListingReviewPageResponse> listListingReviews(
            @PathVariable("id") UUID listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageableValidation.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviewService.listListingReviews(listingId, pageable));
    }
}
