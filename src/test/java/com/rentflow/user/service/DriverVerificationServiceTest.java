package com.rentflow.user.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.DriverVerificationNotFoundException;
import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.user.dto.DriverVerificationResponse;
import com.rentflow.user.dto.ReviewDriverVerificationRequest;
import com.rentflow.user.dto.SubmitDriverLicenseRequest;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
import com.rentflow.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DriverVerificationServiceTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ADMIN_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VERIFICATION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private DriverVerificationRepository driverVerificationRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private EncryptionUtil encryptionUtil;

    private DriverVerificationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-05-28T00:00:00Z"), ZoneOffset.UTC);
        service = new DriverVerificationService(
                driverVerificationRepository,
                userProfileRepository,
                encryptionUtil,
                clock);
    }

    @Test
    void submitRejectsDuplicateActiveVerification() {
        SubmitDriverLicenseRequest request = new SubmitDriverLicenseRequest(
                "A123456789",
                LocalDate.of(2027, 1, 1),
                UUID.randomUUID());
        when(driverVerificationRepository.existsByCustomerIdAndStatusIn(eq(CUSTOMER_ID), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(CUSTOMER_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "ALREADY_SUBMITTED");
        verify(driverVerificationRepository, never()).save(any());
    }

    @Test
    void submitAllowsResubmissionWhenNoActiveVerificationExists() {
        SubmitDriverLicenseRequest request = new SubmitDriverLicenseRequest(
                "A123456789",
                LocalDate.of(2027, 1, 1),
                UUID.randomUUID());
        UserProfile profile = new UserProfile("Customer");
        profile.setUserId(CUSTOMER_ID);

        when(driverVerificationRepository.existsByCustomerIdAndStatusIn(eq(CUSTOMER_ID), any()))
                .thenReturn(false);
        when(encryptionUtil.encrypt("A123456789")).thenReturn("enc");
        when(encryptionUtil.hash("A123456789")).thenReturn("hash");
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile));
        when(driverVerificationRepository.save(any(DriverVerification.class))).thenAnswer(inv -> {
            DriverVerification verification = inv.getArgument(0);
            verification.setId(VERIFICATION_ID);
            verification.setCreatedAt(Instant.parse("2026-05-28T00:00:00Z"));
            return verification;
        });

        DriverVerificationResponse response = service.submit(CUSTOMER_ID, request);

        assertThat(response.id()).isEqualTo(VERIFICATION_ID);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(userProfileRepository).save(profile);
        assertThat(profile.getDriverVerificationStatus())
                .isEqualTo(UserProfile.DriverVerificationStatus.PENDING);
    }

    @Test
    void submitPersistsEncryptedAndHashedLicense() {
        SubmitDriverLicenseRequest request = new SubmitDriverLicenseRequest(
                " abC123-9 ",
                LocalDate.of(2027, 1, 1),
                UUID.randomUUID());
        UserProfile profile = new UserProfile("Customer");
        profile.setUserId(CUSTOMER_ID);

        when(driverVerificationRepository.existsByCustomerIdAndStatusIn(eq(CUSTOMER_ID), any()))
                .thenReturn(false);
        when(encryptionUtil.encrypt("ABC123-9")).thenReturn("enc-value");
        when(encryptionUtil.hash("ABC123-9")).thenReturn("hash-value");
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile));
        when(driverVerificationRepository.save(any(DriverVerification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.submit(CUSTOMER_ID, request);

        ArgumentCaptor<DriverVerification> captor = ArgumentCaptor.forClass(DriverVerification.class);
        verify(driverVerificationRepository).save(captor.capture());
        assertThat(captor.getValue().getLicenseNumberEncrypted()).isEqualTo("enc-value");
        assertThat(captor.getValue().getLicenseNumberHash()).isEqualTo("hash-value");
    }

    @Test
    void approveRequiresPendingStatus() {
        DriverVerification verification = new DriverVerification();
        verification.setId(VERIFICATION_ID);
        verification.setStatus(DriverVerificationStatus.REJECTED);
        when(driverVerificationRepository.findByIdForUpdate(VERIFICATION_ID))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.approve(ADMIN_ID, VERIFICATION_ID, new ReviewDriverVerificationRequest("ok")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "DRIVER_VERIFICATION_INVALID_STATUS");
    }

    @Test
    void rejectRequiresPendingStatus() {
        DriverVerification verification = new DriverVerification();
        verification.setId(VERIFICATION_ID);
        verification.setStatus(DriverVerificationStatus.APPROVED);
        when(driverVerificationRepository.findByIdForUpdate(VERIFICATION_ID))
                .thenReturn(Optional.of(verification));

        assertThatThrownBy(() -> service.reject(ADMIN_ID, VERIFICATION_ID, new ReviewDriverVerificationRequest("no")))
                .isInstanceOf(BusinessRuleException.class)
                .hasFieldOrPropertyWithValue("code", "DRIVER_VERIFICATION_INVALID_STATUS");
    }

    @Test
    void approveUpdatesVerificationAndProfileStatus() {
        DriverVerification verification = new DriverVerification();
        verification.setId(VERIFICATION_ID);
        verification.setCustomerId(CUSTOMER_ID);
        verification.setStatus(DriverVerificationStatus.PENDING);
        UserProfile profile = new UserProfile("Customer");
        profile.setUserId(CUSTOMER_ID);

        when(driverVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).thenReturn(Optional.of(verification));
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile));
        when(driverVerificationRepository.save(verification)).thenReturn(verification);

        DriverVerificationResponse response =
                service.approve(ADMIN_ID, VERIFICATION_ID, new ReviewDriverVerificationRequest("looks good"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(verification.getReviewedBy()).isEqualTo(ADMIN_ID);
        assertThat(profile.getDriverVerificationStatus()).isEqualTo(UserProfile.DriverVerificationStatus.APPROVED);
        verify(userProfileRepository).save(profile);
    }

    @Test
    void rejectUpdatesVerificationAndProfileStatus() {
        DriverVerification verification = new DriverVerification();
        verification.setId(VERIFICATION_ID);
        verification.setCustomerId(CUSTOMER_ID);
        verification.setStatus(DriverVerificationStatus.PENDING);
        UserProfile profile = new UserProfile("Customer");
        profile.setUserId(CUSTOMER_ID);

        when(driverVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).thenReturn(Optional.of(verification));
        when(userProfileRepository.findByUserId(CUSTOMER_ID)).thenReturn(Optional.of(profile));
        when(driverVerificationRepository.save(verification)).thenReturn(verification);

        DriverVerificationResponse response =
                service.reject(ADMIN_ID, VERIFICATION_ID, new ReviewDriverVerificationRequest("mismatch"));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(profile.getDriverVerificationStatus()).isEqualTo(UserProfile.DriverVerificationStatus.REJECTED);
        verify(userProfileRepository).save(profile);
    }

    @Test
    void approveThrowsWhenVerificationMissing() {
        when(driverVerificationRepository.findByIdForUpdate(VERIFICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(ADMIN_ID, VERIFICATION_ID, new ReviewDriverVerificationRequest("ok")))
                .isInstanceOf(DriverVerificationNotFoundException.class)
                .hasFieldOrPropertyWithValue("code", "DRIVER_VERIFICATION_NOT_FOUND");
    }
}
