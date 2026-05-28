package com.rentflow.scheduler;

import com.rentflow.notification.entity.NotificationType;
import com.rentflow.notification.service.NotificationService;
import com.rentflow.user.entity.DriverVerification;
import com.rentflow.user.entity.DriverVerificationStatus;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.DriverVerificationRepository;
import com.rentflow.user.repository.UserProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
public class ExpireDriverVerificationsProcessor {

    private final DriverVerificationRepository driverVerificationRepository;
    private final UserProfileRepository userProfileRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    public ExpireDriverVerificationsProcessor(
            DriverVerificationRepository driverVerificationRepository,
            UserProfileRepository userProfileRepository,
            NotificationService notificationService,
            Clock clock) {
        this.driverVerificationRepository = driverVerificationRepository;
        this.userProfileRepository = userProfileRepository;
        this.notificationService = notificationService;
        this.clock = clock;
    }

    @Transactional
    public int processBatch(int batchSize) {
        LocalDate today = LocalDate.now(clock);
        List<DriverVerification> candidates = driverVerificationRepository
                .findExpiredCandidatesForUpdate(today, batchSize);
        int processed = 0;
        for (DriverVerification verification : candidates) {
            if (verification.getStatus() != DriverVerificationStatus.PENDING
                    && verification.getStatus() != DriverVerificationStatus.APPROVED) {
                continue;
            }

            verification.setStatus(DriverVerificationStatus.EXPIRED);
            driverVerificationRepository.save(verification);

            UUID customerId = verification.getCustomerId();
            UserProfile profile = userProfileRepository.findByUserId(customerId)
                    .orElseThrow(() -> new IllegalStateException("User profile not found for customer: " + customerId));
            profile.setDriverVerificationStatus(UserProfile.DriverVerificationStatus.EXPIRED);
            userProfileRepository.save(profile);
            notificationService.create(
                    customerId,
                    NotificationType.DRIVER_VERIFICATION_EXPIRED,
                    "Driver License Expired",
                    "Your driver license has expired. Please submit a new verification.");
            processed++;
        }
        return processed;
    }
}
