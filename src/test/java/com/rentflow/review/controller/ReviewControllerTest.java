package com.rentflow.review.controller;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.review.dto.CreateReviewRequest;
import com.rentflow.review.dto.ListingReviewPageResponse;
import com.rentflow.review.dto.ReviewResponse;
import com.rentflow.review.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewControllerTest {

    private MockMvc mockMvc;
    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = mock(ReviewService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ReviewController(reviewService)).build();
    }

    @Test
    void createReviewReturns201() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        when(reviewService.createReview(eq(bookingId), eq(new CreateReviewRequest(5, "Great car"))))
                .thenReturn(new ReviewResponse(
                        reviewId,
                        bookingId,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        5,
                        "Great car",
                        Instant.parse("2026-05-29T00:00:00Z")));

        mockMvc.perform(post("/api/v1/bookings/{id}/review", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating":5,
                                  "content":"Great car"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reviewId.toString()))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    void listListingReviewsReturnsPageWithAggregate() throws Exception {
        UUID listingId = UUID.randomUUID();
        when(reviewService.listListingReviews(eq(listingId), any()))
                .thenReturn(new ListingReviewPageResponse(
                        List.of(new ReviewResponse(
                                UUID.randomUUID(),
                                UUID.randomUUID(),
                                listingId,
                                UUID.randomUUID(),
                                4,
                                "Good",
                                Instant.parse("2026-05-29T00:00:00Z"))),
                        0,
                        20,
                        1,
                        1,
                        new BigDecimal("4.00"),
                        1));

        mockMvc.perform(get("/api/v1/listings/{id}/reviews", listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].rating").value(4))
                .andExpect(jsonPath("$.averageRating").value(4.00))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }
}
