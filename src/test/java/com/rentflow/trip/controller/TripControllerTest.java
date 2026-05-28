package com.rentflow.trip.controller;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.trip.dto.CheckInRequest;
import com.rentflow.trip.dto.CheckOutRequest;
import com.rentflow.trip.dto.TripRecordResponse;
import com.rentflow.trip.service.TripService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripControllerTest {

    private MockMvc mockMvc;
    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = mock(TripService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TripController(tripService)).build();
    }

    @Test
    void checkInReturnsTripResponse() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(tripService.checkIn(eq(bookingId), eq(new CheckInRequest(12000, 90, "start"))))
                .thenReturn(new TripRecordResponse(
                        bookingId,
                        BookingStatus.IN_PROGRESS,
                        Instant.parse("2026-05-29T00:00:00Z"),
                        null,
                        12000,
                        null,
                        90,
                        null,
                        "start"));

        mockMvc.perform(post("/api/v1/bookings/{id}/check-in", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":12000,
                                  "fuelLevel":90,
                                  "note":"start"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.checkInOdometer").value(12000));
    }

    @Test
    void checkOutReturnsTripResponse() throws Exception {
        UUID bookingId = UUID.randomUUID();
        when(tripService.checkOut(eq(bookingId), eq(new CheckOutRequest(12200, 70, "end"))))
                .thenReturn(new TripRecordResponse(
                        bookingId,
                        BookingStatus.COMPLETED,
                        Instant.parse("2026-05-29T00:00:00Z"),
                        Instant.parse("2026-05-29T02:00:00Z"),
                        12000,
                        12200,
                        90,
                        70,
                        "end"));

        mockMvc.perform(post("/api/v1/bookings/{id}/check-out", bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "odometer":12200,
                                  "fuelLevel":70,
                                  "note":"end"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.checkOutOdometer").value(12200));
    }
}
