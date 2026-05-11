package com.rentflow.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingService;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.booking.service.CreateBookingRequest;
import com.rentflow.booking.service.RequestedExtra;
import com.rentflow.common.exception.CorrelationIdHelper;
import com.rentflow.common.exception.GlobalExceptionHandler;
import com.rentflow.common.web.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID LISTING_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID EXTRA_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final String VALID_IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private BookingService bookingService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bookingService = mock(BookingService.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createBookingMissingIdempotencyKeyReturnsRequiredError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void createBookingInvalidIdempotencyKeyReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "not-a-v4-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createBookingValidHeaderDelegatesAndReturnsCreated() throws Exception {
        when(bookingService.createBooking(eq(VALID_IDEMPOTENCY_KEY), any(CreateBookingRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("HELD"));

        verify(bookingService).createBooking(eq(VALID_IDEMPOTENCY_KEY), any(CreateBookingRequest.class));
    }

    @Test
    void listMyBookingsDelegatesWithStatusAndPageResponse() throws Exception {
        PageResponse<BookingSummaryResponse> page = new PageResponse<>(
                List.of(summary()),
                0,
                20,
                1,
                1);
        when(bookingService.listMyBookings(eq(BookingStatus.HELD), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/bookings/me")
                        .param("status", "HELD")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(bookingService).listMyBookings(eq(BookingStatus.HELD), any(Pageable.class));
    }

    @Test
    void getBookingDelegatesAndReturnsDetail() throws Exception {
        when(bookingService.getBooking(BOOKING_ID)).thenReturn(response());

        mockMvc.perform(get("/api/v1/bookings/{id}", BOOKING_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.listingTitle").value("Toyota Vios 2022"));
    }

    private CreateBookingRequest request() {
        return new CreateBookingRequest(
                LISTING_ID,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                List.of(new RequestedExtra(EXTRA_ID, 1)));
    }

    private BookingResponse response() {
        return new BookingResponse(
                BOOKING_ID,
                BookingStatus.HELD,
                LISTING_ID,
                "Toyota Vios 2022",
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                UUID.fromString("55555555-5555-4555-8555-555555555555"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                Instant.parse("2026-05-11T00:15:00Z"),
                new BigDecimal("1500000.00"),
                "VND",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                Instant.parse("2026-05-11T00:00:00Z"));
    }

    private BookingSummaryResponse summary() {
        return new BookingSummaryResponse(
                BOOKING_ID,
                BookingStatus.HELD,
                LISTING_ID,
                "Toyota Vios 2022",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                Instant.parse("2026-05-11T00:15:00Z"),
                new BigDecimal("1500000.00"),
                "VND",
                Instant.parse("2026-05-11T00:00:00Z"));
    }
}
