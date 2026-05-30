package com.rentflow.file.dto;

import jakarta.validation.constraints.Min;

public record UpdateListingPhotoRequest(
        Boolean primary,
        @Min(0) Integer displayOrder) {
}
