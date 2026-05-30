package com.rentflow.availability.dto;

import java.time.LocalDate;

public record DateRangeRequest(
        LocalDate from,
        LocalDate to
) {
}
