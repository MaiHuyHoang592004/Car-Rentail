package com.rentflow.file.dto;

import java.time.Instant;
import java.util.UUID;

public record FileUploadIntentResponse(
        UUID fileId,
        String bucket,
        String objectKey,
        String uploadUrl,
        Instant expiresAt) {
}
