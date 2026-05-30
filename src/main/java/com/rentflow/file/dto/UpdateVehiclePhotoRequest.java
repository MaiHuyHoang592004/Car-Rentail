package com.rentflow.file.dto;

import jakarta.validation.constraints.Min;

public record UpdateVehiclePhotoRequest(
        Boolean primary,
        @Min(0) Integer displayOrder) {
}
