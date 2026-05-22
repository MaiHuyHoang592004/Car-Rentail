package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class VehicleBookingAdapter implements VehicleBookingPort {

    private final BookingRepository bookingRepository;

    @Override
    public boolean hasActiveBookings(UUID vehicleId) {
        return bookingRepository.existsActiveBookingsForVehicle(
                vehicleId,
                List.of(BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS));
    }
}
