package com.rentflow.file.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDisputeAttachmentUploadRequest(
        @NotBlank @Size(max = 120) String contentType,
        @NotNull @Min(1) @Max(10 * 1024 * 1024) Long sizeBytes,
        @Size(max = 128) String checksum) {
}
