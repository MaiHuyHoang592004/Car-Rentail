package com.rentflow.file.dto;

import java.time.Instant;
import java.util.UUID;

public record VehiclePhotoResponse(
        UUID id,
        UUID vehicleId,
        UUID fileId,
        boolean primary,
        int displayOrder,
        String visibility,
        String signedUrl,
        Instant signedUrlExpiresAt) {
}
