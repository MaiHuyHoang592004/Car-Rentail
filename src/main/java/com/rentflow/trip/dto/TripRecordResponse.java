package com.rentflow.trip.dto;

import com.rentflow.booking.entity.BookingStatus;

import java.time.Instant;
import java.util.UUID;

public record TripRecordResponse(
        UUID bookingId,
        BookingStatus bookingStatus,
        Instant checkInAt,
        Instant checkOutAt,
        Integer checkInOdometer,
        Integer checkOutOdometer,
        Integer checkInFuelLevel,
        Integer checkOutFuelLevel,
        String notes) {
}
