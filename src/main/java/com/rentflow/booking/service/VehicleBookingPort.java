package com.rentflow.booking.service;

import java.util.UUID;

public interface VehicleBookingPort {

    boolean hasActiveBookings(UUID vehicleId);
}
