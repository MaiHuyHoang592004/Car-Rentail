package com.rentflow.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.service.BookingResponse;
import com.rentflow.booking.service.BookingSummaryResponse;
import com.rentflow.booking.service.HostBookingApprovalService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HostBookingControllerTest {

    private static final UUID BOOKING_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID LISTING_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String VALID_IDEMPOTENCY_KEY = "8b71f8d2-9e1d-4f7a-bbe6-334c3816df91";

    private HostBookingApprovalService hostBookingApprovalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        hostBookingApprovalService = mock(HostBookingApprovalService.class);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new HostBookingController(hostBookingApprovalService))
                .setControllerAdvice(new GlobalExceptionHandler(new CorrelationIdHelper()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void listHostBookingsDelegatesWithStatusAndPagination() throws Exception {
        PageResponse<BookingSummaryResponse> page = new PageResponse<>(List.of(summary()), 0, 20, 1, 1);
        when(hostBookingApprovalService.listHostBookings(eq(BookingStatus.PENDING_HOST_APPROVAL), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/host/bookings")
                        .param("status", "PENDING_HOST_APPROVAL")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(BOOKING_ID.toString()));

        verify(hostBookingApprovalService).listHostBookings(eq(BookingStatus.PENDING_HOST_APPROVAL), any(Pageable.class));
    }

    @Test
    void listHostBookingsRejectsInvalidStatus() throws Exception {
        mockMvc.perform(get("/api/v1/host/bookings")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(hostBookingApprovalService, never()).listHostBookings(any(), any());
    }

    @Test
    void approveBookingRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", BOOKING_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(hostBookingApprovalService, never()).approveBooking(any(), any());
    }

    @Test
    void approveBookingRejectsInvalidIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", BOOKING_ID)
                        .header("Idempotency-Key", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(hostBookingApprovalService, never()).approveBooking(any(), any());
    }

    @Test
    void approveBookingDelegatesAndReturnsBookingResponse() throws Exception {
        when(hostBookingApprovalService.approveBooking(BOOKING_ID, VALID_IDEMPOTENCY_KEY))
                .thenReturn(response(BookingStatus.CONFIRMED));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/approve", BOOKING_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(BOOKING_ID.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectBookingRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/host/bookings/{id}/reject", BOOKING_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));

        verify(hostBookingApprovalService, never()).rejectBooking(any(), any());
    }

    @Test
    void rejectBookingDelegatesAndReturnsBookingResponse() throws Exception {
        when(hostBookingApprovalService.rejectBooking(BOOKING_ID, VALID_IDEMPOTENCY_KEY))
                .thenReturn(response(BookingStatus.REJECTED));

        mockMvc.perform(post("/api/v1/host/bookings/{id}/reject", BOOKING_ID)
                        .header("Idempotency-Key", VALID_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    private BookingSummaryResponse summary() {
        return new BookingSummaryResponse(
                BOOKING_ID,
                BookingStatus.PENDING_HOST_APPROVAL,
                LISTING_ID,
                "Toyota Vios 2022",
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                null,
                new BigDecimal("1500000.00"),
                "VND",
                Instant.parse("2026-05-11T00:00:00Z"));
    }

    private BookingResponse response(BookingStatus status) {
        return new BookingResponse(
                BOOKING_ID,
                status,
                LISTING_ID,
                "Toyota Vios 2022",
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                UUID.fromString("44444444-4444-4444-8444-444444444444"),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 3),
                "Hanoi",
                "Hanoi",
                null,
                new BigDecimal("1500000.00"),
                "VND",
                new ObjectMapper().createObjectNode(),
                new ObjectMapper().createObjectNode(),
                Instant.parse("2026-05-11T00:00:00Z"));
    }
}
