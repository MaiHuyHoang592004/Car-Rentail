package com.rentflow.availability.dto;

import com.rentflow.availability.entity.AvailabilityStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HostDateStatus(
    LocalDate date,
    AvailabilityStatus status,
    List<UUID> bookingId,
    Instant expiresAt
) {}
