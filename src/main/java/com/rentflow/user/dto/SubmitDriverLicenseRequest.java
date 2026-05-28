package com.rentflow.user.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record SubmitDriverLicenseRequest(
        @NotBlank(message = "licenseNumber is required")
        @Size(max = 40, message = "licenseNumber must be at most 40 characters")
        @Pattern(regexp = "^[A-Za-z0-9\\-]+$", message = "licenseNumber format is invalid")
        String licenseNumber,

        @NotNull(message = "licenseExpiryDate is required")
        @FutureOrPresent(message = "licenseExpiryDate must be today or in the future")
        LocalDate licenseExpiryDate,

        @NotNull(message = "documentFileId is required")
        UUID documentFileId
) {}
