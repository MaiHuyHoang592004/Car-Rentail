package com.rentflow.review.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BookingNotFoundException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.review.dto.CreateReviewRequest;
import com.rentflow.review.dto.ListingReviewPageResponse;
import com.rentflow.review.dto.ReviewResponse;
import com.rentflow.review.entity.Review;
import com.rentflow.review.repository.ReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final ListingRepository listingRepository;
    private final SecurityContext securityContext;

    public ReviewService(
            ReviewRepository reviewRepository,
            BookingRepository bookingRepository,
            ListingRepository listingRepository,
            SecurityContext securityContext) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.listingRepository = listingRepository;
        this.securityContext = securityContext;
    }

    @Transactional
    public ReviewResponse createReview(UUID bookingId, CreateReviewRequest request) {
        UUID reviewerId = securityContext.currentUserId();
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId.toString()));
        if (!booking.getCustomerId().equals(reviewerId)) {
            throw new BookingNotFoundException(bookingId.toString());
        }
        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new BusinessRuleException("BOOKING_INVALID_STATUS", "Review is allowed only for COMPLETED bookings");
        }
        if (reviewRepository.existsByBookingIdAndReviewerId(bookingId, reviewerId)) {
            throw new BusinessRuleException("REVIEW_ALREADY_EXISTS", "Review already exists for this booking");
        }

        Review review = new Review();
        review.setBookingId(bookingId);
        review.setListingId(booking.getListingId());
        review.setReviewerId(reviewerId);
        review.setRating(request.rating());
        review.setContent(request.content());
        review = reviewRepository.save(review);
        reviewRepository.flush();

        refreshListingAggregate(booking.getListingId());
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public ListingReviewPageResponse listListingReviews(UUID listingId, Pageable pageable) {
        if (listingRepository.findById(listingId).isEmpty()) {
            throw new ListingNotFoundException(listingId.toString());
        }
        Page<ReviewResponse> page = reviewRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(ReviewResponse::from);
        ReviewRepository.RatingAggregate aggregate = reviewRepository.loadRatingAggregate(listingId);
        return ListingReviewPageResponse.from(page, aggregate.averageRating(), aggregate.reviewCount());
    }

    private void refreshListingAggregate(UUID listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        ReviewRepository.RatingAggregate aggregate = reviewRepository.loadRatingAggregate(listingId);
        BigDecimal normalizedAverage = aggregate.averageRating().setScale(2, RoundingMode.HALF_UP);
        listing.setAverageRating(normalizedAverage);
        listing.setReviewCount(aggregate.reviewCount());
        listingRepository.save(listing);
    }
}
