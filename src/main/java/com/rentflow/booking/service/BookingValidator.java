package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.DriverLicenseNotApprovedException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.user.entity.UserProfile;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * I24: validation logic extracted from BookingService. Holds no transactional
 * boundary — callers run inside their own @Transactional context.
 */
@Component
public class BookingValidator {

    private static final List<BookingStatus> CUSTOMER_OVERLAP_STATUSES = List.of(
            BookingStatus.HELD, BookingStatus.PENDING_HOST_APPROVAL,
            BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);

    private final ListingRepository listingRepository;
    private final BookingRepository bookingRepository;
    private final UserProfileRepository userProfileRepository;
    private final VehicleRepository vehicleRepository;
    private final Clock clock;
    private final boolean requireDriverVerification;

    public BookingValidator(
            ListingRepository listingRepository,
            BookingRepository bookingRepository,
            UserProfileRepository userProfileRepository,
            VehicleRepository vehicleRepository,
            Clock clock,
            @Value("${rentflow.booking.require-driver-verification:false}") boolean requireDriverVerification) {
        this.listingRepository = listingRepository;
        this.bookingRepository = bookingRepository;
        this.userProfileRepository = userProfileRepository;
        this.vehicleRepository = vehicleRepository;
        this.clock = clock;
        this.requireDriverVerification = requireDriverVerification;
    }

    public Listing resolveListingForBooking(UUID listingId, UUID customerId) {
        Listing listing = listingRepository
                .findByIdAndStatusWithExtras(listingId, ListingStatus.ACTIVE)
                .filter(this::vehicleIsActive)
                .orElseThrow(() -> new ListingNotFoundException(String.valueOf(listingId)));
        if (listing.getHostId().equals(customerId)) {
            throw new AccessDeniedException();
        }
        return listing;
    }

    public long validateDates(LocalDate pickupDate, LocalDate returnDate) {
        if (pickupDate == null || returnDate == null) {
            throw new ValidationException("pickupDate and returnDate are required");
        }
        if (pickupDate.isBefore(LocalDate.now(clock))) {
            throw new ValidationException("pickupDate must not be in the past");
        }
        if (!pickupDate.isBefore(returnDate)) {
            throw new ValidationException("pickupDate must be before returnDate");
        }
        long rentalDays = ChronoUnit.DAYS.between(pickupDate, returnDate);
        if (rentalDays < 1 || rentalDays > 30) {
            throw new ValidationException("rentalDays must be between 1 and 30");
        }
        return rentalDays;
    }

    public void validateCustomerOverlap(UUID customerId, LocalDate pickupDate, LocalDate returnDate) {
        boolean overlaps = bookingRepository.existsOverlappingActiveBooking(
                customerId, pickupDate, returnDate, CUSTOMER_OVERLAP_STATUSES);
        if (overlaps) {
            throw new BusinessRuleException(
                    "BOOKING_OVERLAP_CUSTOMER",
                    "Customer already has an active overlapping booking");
        }
    }

    public void validateDriverVerification(UUID customerId) {
        if (!requireDriverVerification) {
            return;
        }
        UserProfile profile = userProfileRepository.findByUserId(customerId)
                .orElseThrow(() -> new DriverLicenseNotApprovedException(
                        "DRIVER_VERIFICATION_REQUIRED",
                        "Driver verification must be submitted before booking"));
        switch (profile.getDriverVerificationStatus()) {
            case APPROVED -> {
                return;
            }
            case NOT_SUBMITTED -> throw new DriverLicenseNotApprovedException(
                    "DRIVER_VERIFICATION_REQUIRED",
                    "Driver verification must be submitted before booking");
            case PENDING -> throw new DriverLicenseNotApprovedException(
                    "DRIVER_VERIFICATION_PENDING",
                    "Driver verification is pending approval");
            case REJECTED, EXPIRED -> throw new DriverLicenseNotApprovedException(
                    "DRIVER_VERIFICATION_REJECTED",
                    "Driver verification must be resubmitted before booking");
        }
    }

    private boolean vehicleIsActive(Listing listing) {
        return vehicleRepository.findById(listing.getVehicleId())
                .map(vehicle -> vehicle.getStatus() == VehicleStatus.ACTIVE)
                .orElse(false);
    }
}
