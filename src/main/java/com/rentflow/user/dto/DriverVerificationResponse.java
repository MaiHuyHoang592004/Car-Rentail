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
        String documentPreviewUrl,
        Long pendingAgeHours,
        Boolean slaBreached,
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
                null,
                null,
                null,
                verification.getReviewReason(),
                verification.getReviewedBy(),
                verification.getReviewedAt(),
                verification.getCreatedAt());
    }

    public static DriverVerificationResponse from(
            DriverVerification verification,
            String documentPreviewUrl,
            Long pendingAgeHours,
            Boolean slaBreached) {
        return new DriverVerificationResponse(
                verification.getId(),
                verification.getCustomerId(),
                verification.getStatus().name(),
                verification.getLicenseExpiryDate(),
                verification.getDocumentFileId(),
                documentPreviewUrl,
                pendingAgeHours,
                slaBreached,
                verification.getReviewReason(),
                verification.getReviewedBy(),
                verification.getReviewedAt(),
                verification.getCreatedAt());
    }
}
