package com.rentflow.availability.dto;

import java.time.LocalDate;

public record ExtendAvailabilityRequest(
        LocalDate throughDate
) {
}
