package com.rentflow.booking.service;

public record PatchBookingLocationRequest(
        String pickupLocation,
        String returnLocation) {
}
