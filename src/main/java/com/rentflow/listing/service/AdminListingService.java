package com.rentflow.listing.service;

import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.availability.service.AvailabilityService;
import com.rentflow.auth.entity.AuthUser;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.listing.dto.AdminListingDetailResponse;
import com.rentflow.listing.dto.ExtraResponse;
import org.springframework.dao.DataIntegrityViolationException;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.mapper.ListingMapper;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AdminListingService {

    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final AvailabilityCalendarRepository availabilityRepository;
    private final AvailabilityService availabilityService;
    private final ListingStateMachine stateMachine;
    private final ListingMapper mapper;
    private final ExtraRepository extraRepository;
    private final UserProfileRepository userProfileRepository;
    private final AuthUserRepository authUserRepository;

    public AdminListingService(ListingRepository listingRepository,
                               VehicleRepository vehicleRepository,
                               AvailabilityCalendarRepository availabilityRepository,
                               AvailabilityService availabilityService,
                               ListingStateMachine stateMachine,
                               ListingMapper mapper,
                               ExtraRepository extraRepository,
                               UserProfileRepository userProfileRepository,
                               AuthUserRepository authUserRepository) {
        this.listingRepository = listingRepository;
        this.vehicleRepository = vehicleRepository;
        this.availabilityRepository = availabilityRepository;
        this.availabilityService = availabilityService;
        this.stateMachine = stateMachine;
        this.mapper = mapper;
        this.extraRepository = extraRepository;
        this.userProfileRepository = userProfileRepository;
        this.authUserRepository = authUserRepository;
    }

    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> listListings(ListingStatus status, UUID hostId, String city, Pageable pageable) {
        Page<Listing> listings = listingRepository.findByFilters(status, hostId, city, pageable);
        return mapper.toSummaryPage(listings);
    }

    @Transactional(readOnly = true)
    public AdminListingDetailResponse getListing(UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        long generatedDays = availabilityRepository.countByListingId(listingId);

        var profile = userProfileRepository.findByUserId(listing.getHostId()).orElse(null);
        String hostEmail = authUserRepository.findById(listing.getHostId())
                .map(AuthUser::getEmail).orElse(null);
        AdminListingDetailResponse.HostSummary hostSummary = new AdminListingDetailResponse.HostSummary(
                listing.getHostId(),
                profile != null ? profile.getFullName() : null,
                hostEmail
        );

        ListingResponse.VehicleSummary vehicleSummary = null;
        List<ExtraResponse> extras = extraRepository.findByListingId(listingId).stream()
                .map(ExtraResponse::from).toList();
        if (vehicle != null) {
            vehicleSummary = new ListingResponse.VehicleSummary(
                    vehicle.getCategory(),
                    vehicle.getMake(),
                    vehicle.getModel(),
                    vehicle.getManufactureYear(),
                    vehicle.getTransmission(),
                    vehicle.getFuelType(),
                    vehicle.getSeats(),
                    vehicle.getStatus()
            );
        }

        ListingResponse listingResponse = ListingResponse.from(listing, vehicleSummary, extras, generatedDays);

        return new AdminListingDetailResponse(
                listingResponse,
                hostSummary,
                new AdminListingDetailResponse.BookingSummary(0)
        );
    }

    @Transactional
    public ListingResponse approveListing(UUID listingId) {
        // Lock listing AND vehicle with SELECT FOR UPDATE to prevent race conditions
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        Vehicle vehicle = vehicleRepository.findByIdForUpdate(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        // Validate preconditions
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE before listing can be approved. Current status: " + vehicle.getStatus());
        }

        if (listing.getStatus() != ListingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Listing must be PENDING_APPROVAL to be approved. Current status: " + listing.getStatus());
        }

        // Check one ACTIVE listing per vehicle constraint
        boolean hasActive = listingRepository.existsByVehicleIdAndStatus(
                vehicle.getId(), ListingStatus.ACTIVE);
        if (hasActive) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an ACTIVE listing");
        }

        // Transition listing to ACTIVE
        listing.setStatus(ListingStatus.ACTIVE);
        try {
            listingRepository.save(listing);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an active listing");
        }

        // Generate 365 availability rows in the same transaction
        availabilityService.generateForListing(listingId);

        // TODO (Phase 7/9): Insert outbox event for LISTING_APPROVED:
        //   INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at)
        //   VALUES ('Listing', :listingId, 'LISTING_APPROVED',
        //           '{"listingId": :listingId, "hostId": :hostId, "approvedAt": :approvedAt}'::jsonb,
        //           'NEW', NOW(), NOW());

        log.info("Listing approved: {} (vehicle: {})", listingId, vehicle.getId());

        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse rejectListing(UUID listingId, String reason) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only PENDING_APPROVAL listings can be rejected");
        }

        stateMachine.validateTransition(listing.getStatus(), ListingStatus.DRAFT);
        listing.setStatus(ListingStatus.DRAFT);
        listingRepository.save(listing);

        // TODO (Phase 7/9): Insert outbox event for LISTING_REJECTED:
        //   INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at)
        //   VALUES ('Listing', :listingId, 'LISTING_REJECTED',
        //           '{"listingId": :listingId, "hostId": :hostId, "reason": :reason, "rejectedAt": :rejectedAt}'::jsonb,
        //           'NEW', NOW(), NOW());

        log.info("Listing rejected: {} reason: {}", listingId, reason);

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse suspendListing(UUID listingId, String reason) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (!stateMachine.canTransition(listing.getStatus(), ListingStatus.SUSPENDED)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot suspend listing from status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.SUSPENDED);
        listingRepository.save(listing);

        log.info("Listing suspended: {} reason: {}", listingId, reason);

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse reactivateListing(UUID listingId) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.SUSPENDED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only SUSPENDED listings can be reactivated");
        }

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE to reactivate listing");
        }

        listing.setStatus(ListingStatus.ACTIVE);
        listingRepository.save(listing);

        log.info("Listing reactivated: {}", listingId);

        return mapper.toResponse(listing, vehicle, List.of());
    }
}
