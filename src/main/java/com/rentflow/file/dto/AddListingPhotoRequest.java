package com.rentflow.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddListingPhotoRequest(
        @NotBlank String bucket,
        @NotBlank String objectKey,
        @NotBlank @Size(max = 120) String contentType,
        @NotNull @Positive Long sizeBytes,
        @Size(max = 128) String checksum,
        Boolean primary) {
}
