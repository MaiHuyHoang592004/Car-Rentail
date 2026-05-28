package com.rentflow.file.dto;

import java.time.Instant;
import java.util.UUID;

public record ListingPhotoResponse(
        UUID id,
        UUID listingId,
        UUID fileId,
        boolean primary,
        int displayOrder,
        String visibility,
        String signedUrl,
        Instant signedUrlExpiresAt) {
}
