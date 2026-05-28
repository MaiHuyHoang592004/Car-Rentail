package com.rentflow.scheduler;

import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpireDriverVerificationsProcessorTest {

    private static final Instant NOW = Instant.parse("2026-05-28T00:00:00Z");
    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_CUSTOMER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private DriverVerificationRepository driverVerificationRepository;
    private UserProfileRepository userProfileRepository;
    private NotificationService notificationService;
    private ExpireDriverVerificationsProcessor processor;

    @BeforeEach
    void setUp() {
        driverVerificationRepository = mock(DriverVerificationRepository.class);
        userProfileRepository = mock(UserProfileRepository.class);
        notificationService = mock(NotificationService.class);
        processor = new ExpireDriverVerificationsProcessor(
                driverVerificationRepository,
                userProfileRepository,
                notificationService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void overduePendingAndApprovedBecomeExpiredAndSyncProfile() {
        DriverVerification pending = verification(CUSTOMER_ID, DriverVerificationStatus.PENDING);
        DriverVerification approved = verification(OTHER_CUSTOMER_ID, DriverVerificationStatus.APPROVED);
        UserProfile firstProfile = profile(CUSTOMER_ID, UserProfile.DriverVerificationStatus.PENDING);
        UserProfile secondProfile = profile(OTHER_CUSTOMER_ID, UserProfile.DriverVerificationStatus.APPROVED);
        LocalDate today = LocalDate.of(2026, 5, 28);

        when(driverVerificationRepository.findExpiredCandidatesForUpdate(today, 100))
                .thenReturn(List.of(pending, approved));
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(firstProfile));
        when(userProfileRepository.findByUserId(OTHER_CUSTOMER_ID)).thenReturn(Optional.of(secondProfile));

        int processed = processor.processBatch(100);

        assertThat(processed).isEqualTo(2);
        assertThat(pending.getStatus()).isEqualTo(DriverVerificationStatus.EXPIRED);
        assertThat(approved.getStatus()).isEqualTo(DriverVerificationStatus.EXPIRED);
        assertThat(firstProfile.getDriverVerificationStatus()).isEqualTo(UserProfile.DriverVerificationStatus.EXPIRED);
        assertThat(secondProfile.getDriverVerificationStatus()).isEqualTo(UserProfile.DriverVerificationStatus.EXPIRED);
        verify(driverVerificationRepository).save(pending);
        verify(driverVerificationRepository).save(approved);
        verify(userProfileRepository).save(firstProfile);
        verify(userProfileRepository).save(secondProfile);
        verify(notificationService).create(
                CUSTOMER_ID,
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");
        verify(notificationService).create(
                OTHER_CUSTOMER_ID,
                NotificationType.DRIVER_VERIFICATION_EXPIRED,
                "Driver License Expired",
                "Your driver license has expired. Please submit a new verification.");
    }

    @Test
    void nonPendingAndNonApprovedRowsAreSkipped() {
        DriverVerification rejected = verification(CUSTOMER_ID, DriverVerificationStatus.REJECTED);
        LocalDate today = LocalDate.of(2026, 5, 28);
        when(driverVerificationRepository.findExpiredCandidatesForUpdate(today, 100))
                .thenReturn(List.of(rejected));

        int processed = processor.processBatch(100);

        assertThat(processed).isZero();
        verify(driverVerificationRepository, never()).save(any(DriverVerification.class));
        verify(userProfileRepository, never()).findByUserId(any());
        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    @Test
    void emptyCandidateListDoesNothing() {
        LocalDate today = LocalDate.of(2026, 5, 28);
        when(driverVerificationRepository.findExpiredCandidatesForUpdate(today, 100)).thenReturn(List.of());

        int processed = processor.processBatch(100);

        assertThat(processed).isZero();
        verify(driverVerificationRepository, never()).save(any(DriverVerification.class));
        verify(userProfileRepository, never()).save(any(UserProfile.class));
        verify(notificationService, never()).create(any(), any(), any(), any());
    }

    private DriverVerification verification(UUID customerId, DriverVerificationStatus status) {
        DriverVerification verification = new DriverVerification();
        verification.setCustomerId(customerId);
        verification.setStatus(status);
        verification.setLicenseNumberEncrypted("enc");
        verification.setLicenseNumberHash("hash");
        verification.setLicenseExpiryDate(LocalDate.of(2026, 5, 1));
        verification.setDocumentFileId(UUID.randomUUID());
        return verification;
    }

    private UserProfile profile(UUID userId, UserProfile.DriverVerificationStatus status) {
        UserProfile profile = new UserProfile("Test");
        profile.setUserId(userId);
        profile.setDriverVerificationStatus(status);
        return profile;
    }
}
