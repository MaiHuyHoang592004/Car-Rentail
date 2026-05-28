package com.rentflow.review.service;

import com.rentflow.booking.entity.Booking;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.security.SecurityContext;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.review.dto.CreateReviewRequest;
import com.rentflow.review.dto.ReviewResponse;
import com.rentflow.review.entity.Review;
import com.rentflow.review.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private ListingRepository listingRepository;
    @Mock private SecurityContext securityContext;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository, listingRepository, securityContext);
    }

    @Test
    void createReviewForCompletedBookingUpdatesAggregate() {
        UUID bookingId = UUID.randomUUID();
        UUID listingId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setListingId(listingId);
        booking.setCustomerId(reviewerId);
        booking.setStatus(BookingStatus.COMPLETED);
        Listing listing = new Listing();
        listing.setId(listingId);

        when(securityContext.currentUserId()).thenReturn(reviewerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        when(reviewRepository.existsByBookingIdAndReviewerId(bookingId, reviewerId)).thenReturn(false);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review review = invocation.getArgument(0);
            review.setId(UUID.randomUUID());
            return review;
        });
        when(listingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(reviewRepository.loadRatingAggregate(listingId))
                .thenReturn(new ReviewRepository.RatingAggregate(new BigDecimal("4.50"), 2));
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReviewResponse response = reviewService.createReview(bookingId, new CreateReviewRequest(5, "great trip"));

        assertThat(response.bookingId()).isEqualTo(bookingId);
        ArgumentCaptor<Listing> listingCaptor = ArgumentCaptor.forClass(Listing.class);
        verify(listingRepository).save(listingCaptor.capture());
        assertThat(listingCaptor.getValue().getAverageRating()).isEqualByComparingTo("4.50");
        assertThat(listingCaptor.getValue().getReviewCount()).isEqualTo(2);
    }

    @Test
    void createReviewRejectsNonCompletedBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Booking booking = new Booking();
        booking.setId(bookingId);
        booking.setCustomerId(reviewerId);
        booking.setStatus(BookingStatus.CONFIRMED);
        when(securityContext.currentUserId()).thenReturn(reviewerId);
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> reviewService.createReview(bookingId, new CreateReviewRequest(5, "not allowed")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Review is allowed only for COMPLETED bookings");
    }
}
