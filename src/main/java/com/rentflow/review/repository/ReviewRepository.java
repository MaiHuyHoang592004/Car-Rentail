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

    long countByListingId(UUID listingId);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.listingId = :listingId")
    BigDecimal averageRatingByListingId(@Param("listingId") UUID listingId);

    default RatingAggregate loadRatingAggregate(UUID listingId) {
        long count = countByListingId(listingId);
        if (count == 0) {
            return new RatingAggregate(BigDecimal.ZERO, 0);
        }
        BigDecimal average = averageRatingByListingId(listingId);
        if (average == null) {
            average = BigDecimal.ZERO;
        }
        return new RatingAggregate(average, Math.toIntExact(count));
    }

    record RatingAggregate(BigDecimal averageRating, int reviewCount) {
    }
}
