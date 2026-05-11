package com.rentflow.availability.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HostAvailabilityResponse(
    UUID listingId,
    LocalDate from,
    LocalDate to,
    List<HostDateStatus> dates
) {}
