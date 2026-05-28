package com.rentflow.review.repository;

import com.rentflow.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    boolean existsByBookingIdAndReviewerId(UUID bookingId, UUID reviewerId);

    Page<Review> findByListingIdOrderByCreatedAtDesc(UUID listingId, Pageable pageable);

    @Query("SELECT AVG(r.rating), COUNT(r) FROM Review r WHERE r.listingId = :listingId")
    Object[] aggregateRatingByListingId(@Param("listingId") UUID listingId);

    default RatingAggregate loadRatingAggregate(UUID listingId) {
        Object[] tuple = aggregateRatingByListingId(listingId);
        if (tuple == null || tuple.length < 2 || tuple[1] == null) {
            return new RatingAggregate(BigDecimal.ZERO, 0);
        }
        BigDecimal average = tuple[0] == null ? BigDecimal.ZERO : new BigDecimal(tuple[0].toString());
        Number countNumber = (Number) tuple[1];
        return new RatingAggregate(average, countNumber.intValue());
    }

    record RatingAggregate(BigDecimal averageRating, int reviewCount) {
    }
}
