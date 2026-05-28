package com.rentflow.user.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.DriverVerificationNotFoundException;
import com.rentflow.common.exception.ResourceNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.user.dto.DriverVerificationResponse;
import com.rentflow.user.dto.ReviewDriverVerificationRequest;
import com.rentflow.user.dto.SubmitDriverLicenseRequest;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
import com.rentflow.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DriverVerificationService {

    private static final Set<DriverVerificationStatus> ACTIVE_STATUSES =
            EnumSet.of(DriverVerificationStatus.PENDING, DriverVerificationStatus.APPROVED);

    private final DriverVerificationRepository driverVerificationRepository;
    private final UserProfileRepository userProfileRepository;
    private final EncryptionUtil encryptionUtil;
    private final Clock clock;

    @Transactional
    public DriverVerificationResponse submit(UUID customerId, SubmitDriverLicenseRequest request) {
        LocalDate today = LocalDate.now(clock);
        if (request.licenseExpiryDate().isBefore(today)) {
            throw new ValidationException("licenseExpiryDate must be today or in the future");
        }

        if (driverVerificationRepository.existsByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES)) {
            throw new BusinessRuleException("ALREADY_SUBMITTED", "Verification already submitted");
        }

        String normalizedLicenseNumber = normalizeLicenseNumber(request.licenseNumber());
        DriverVerification verification = new DriverVerification();
        verification.setCustomerId(customerId);
        verification.setStatus(DriverVerificationStatus.PENDING);
        verification.setLicenseNumberEncrypted(encryptionUtil.encrypt(normalizedLicenseNumber));
        verification.setLicenseNumberHash(encryptionUtil.hash(normalizedLicenseNumber));
        verification.setLicenseExpiryDate(request.licenseExpiryDate());
        verification.setDocumentFileId(request.documentFileId());
        verification = driverVerificationRepository.save(verification);

        syncProfileStatus(customerId, DriverVerificationStatus.PENDING);
        return DriverVerificationResponse.from(verification);
    }

    @Transactional
    public DriverVerificationResponse approve(UUID adminId, UUID verificationId, ReviewDriverVerificationRequest request) {
        DriverVerification verification = driverVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new DriverVerificationNotFoundException(verificationId.toString()));
        ensurePending(verification);

        verification.setStatus(DriverVerificationStatus.APPROVED);
        verification.setReviewReason(normalizeReason(request.reason()));
        verification.setReviewedBy(adminId);
        verification.setReviewedAt(Instant.now(clock));
        verification = driverVerificationRepository.save(verification);

        syncProfileStatus(verification.getCustomerId(), DriverVerificationStatus.APPROVED);
        return DriverVerificationResponse.from(verification);
    }

    @Transactional
    public DriverVerificationResponse reject(UUID adminId, UUID verificationId, ReviewDriverVerificationRequest request) {
        DriverVerification verification = driverVerificationRepository.findByIdForUpdate(verificationId)
                .orElseThrow(() -> new DriverVerificationNotFoundException(verificationId.toString()));
        ensurePending(verification);

        verification.setStatus(DriverVerificationStatus.REJECTED);
        verification.setReviewReason(normalizeReason(request.reason()));
        verification.setReviewedBy(adminId);
        verification.setReviewedAt(Instant.now(clock));
        verification = driverVerificationRepository.save(verification);

        syncProfileStatus(verification.getCustomerId(), DriverVerificationStatus.REJECTED);
        return DriverVerificationResponse.from(verification);
    }

    @Transactional(readOnly = true)
    public Page<DriverVerificationResponse> list(DriverVerificationStatus status, Pageable pageable) {
        Page<DriverVerification> page = status == null
                ? driverVerificationRepository.findAll(pageable)
                : driverVerificationRepository.findByStatus(status, pageable);
        return page.map(DriverVerificationResponse::from);
    }

    private void ensurePending(DriverVerification verification) {
        if (verification.getStatus() != DriverVerificationStatus.PENDING) {
            throw new BusinessRuleException(
                    "DRIVER_VERIFICATION_INVALID_STATUS",
                    "Driver verification can only be reviewed from PENDING status");
        }
    }

    private void syncProfileStatus(UUID customerId, DriverVerificationStatus status) {
        UserProfile profile = userProfileRepository.findByUserId(customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "USER_PROFILE_NOT_FOUND", "User profile", customerId.toString()));
        profile.setDriverVerificationStatus(toProfileStatus(status));
        userProfileRepository.save(profile);
    }

    private UserProfile.DriverVerificationStatus toProfileStatus(DriverVerificationStatus status) {
        return switch (status) {
            case PENDING -> UserProfile.DriverVerificationStatus.PENDING;
            case APPROVED -> UserProfile.DriverVerificationStatus.APPROVED;
            case REJECTED -> UserProfile.DriverVerificationStatus.REJECTED;
            case EXPIRED -> UserProfile.DriverVerificationStatus.EXPIRED;
        };
    }

    private String normalizeLicenseNumber(String licenseNumber) {
        return licenseNumber == null ? null : licenseNumber.trim().toUpperCase();
    }

    private String normalizeReason(String reason) {
        return reason == null ? null : reason.trim();
    }
}
