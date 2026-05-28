package com.rentflow.review.dto;

import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.util.List;

public record ListingReviewPageResponse(
        List<ReviewResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        BigDecimal averageRating,
        int reviewCount) {

    public static ListingReviewPageResponse from(
            Page<ReviewResponse> page,
            BigDecimal averageRating,
            int reviewCount) {
        return new ListingReviewPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                averageRating,
                reviewCount);
    }
}
