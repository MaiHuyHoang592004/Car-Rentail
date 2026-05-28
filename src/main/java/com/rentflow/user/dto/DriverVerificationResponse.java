package com.rentflow.user.dto;

import com.rentflow.user.entity.DriverVerification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DriverVerificationResponse(
        UUID id,
        UUID customerId,
        String status,
        LocalDate licenseExpiryDate,
        UUID documentFileId,
        String reviewReason,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant submittedAt
) {

    public static DriverVerificationResponse from(DriverVerification verification) {
        return new DriverVerificationResponse(
                verification.getId(),
                verification.getCustomerId(),
                verification.getStatus().name(),
                verification.getLicenseExpiryDate(),
                verification.getDocumentFileId(),
                verification.getReviewReason(),
                verification.getReviewedBy(),
                verification.getReviewedAt(),
                verification.getCreatedAt());
    }
}
