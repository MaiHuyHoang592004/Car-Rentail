package com.rentflow.trip.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CheckOutRequest(
        @NotNull @PositiveOrZero Integer odometer,
        @NotNull @Min(0) @Max(100) Integer fuelLevel,
        @Size(max = 500) String note) {
}
