package com.rentflow.availability.dto;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

public record PublicAvailabilityResponse(
    UUID listingId,
    Map<LocalDate, String> availability
) {}
