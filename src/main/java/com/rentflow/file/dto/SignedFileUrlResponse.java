package com.rentflow.file.dto;

import java.time.Instant;
import java.util.UUID;

public record SignedFileUrlResponse(
        UUID fileId,
        String visibility,
        String signedUrl,
        Instant expiresAt) {
}
