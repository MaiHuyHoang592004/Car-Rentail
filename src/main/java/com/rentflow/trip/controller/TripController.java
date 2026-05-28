package com.rentflow.trip.controller;

import com.rentflow.trip.dto.CheckInRequest;
import com.rentflow.trip.dto.CheckOutRequest;
import com.rentflow.trip.dto.TripRecordResponse;
import com.rentflow.trip.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping("/{id}/check-in")
    public ResponseEntity<TripRecordResponse> checkIn(
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.ok(tripService.checkIn(bookingId, request));
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<TripRecordResponse> checkOut(
            @PathVariable("id") UUID bookingId,
            @Valid @RequestBody CheckOutRequest request) {
        return ResponseEntity.ok(tripService.checkOut(bookingId, request));
    }
}
