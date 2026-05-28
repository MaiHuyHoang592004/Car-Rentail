package com.rentflow.review.dto;

import com.rentflow.review.entity.Review;

import java.time.Instant;
import java.util.UUID;

public record ReviewResponse(
        UUID id,
        UUID bookingId,
        UUID listingId,
        UUID reviewerId,
        Integer rating,
        String content,
        Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getBookingId(),
                review.getListingId(),
                review.getReviewerId(),
                review.getRating(),
                review.getContent(),
                review.getCreatedAt());
    }
}
