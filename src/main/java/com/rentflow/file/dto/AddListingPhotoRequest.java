package com.rentflow.file.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record AddListingPhotoRequest(
        @NotNull UUID fileId,
        Boolean primary,
        @PositiveOrZero Integer displayOrder) {
}
