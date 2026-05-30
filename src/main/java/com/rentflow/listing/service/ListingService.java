package com.rentflow.listing.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.listing.dto.CreateListingRequest;
import com.rentflow.listing.dto.ExtraResponse;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.dto.CreateExtraRequest;
import com.rentflow.listing.dto.UpdateExtraRequest;
import com.rentflow.listing.dto.UpdateListingRequest;
import com.rentflow.listing.entity.Extra;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.entity.PricingType;
import com.rentflow.listing.mapper.ListingMapper;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;
    private final ExtraRepository extraRepository;
    private final ListingMapper mapper;
    private final ListingStateMachine stateMachine;
    private final AvailabilityCalendarRepository availabilityRepository;

    @Transactional
    public ListingResponse createListing(CreateListingRequest request, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND",
                        "Vehicle not found: " + request.vehicleId()));

        if (!vehicle.getHostId().equals(hostId)) {
            throw new BusinessRuleException("ACCESS_DENIED", "Vehicle does not belong to this host");
        }

        if (vehicle.getStatus() != VehicleStatus.ACTIVE && vehicle.getStatus() != VehicleStatus.DRAFT) {
            throw new BusinessRuleException("INVALID_VEHICLE_STATUS",
                    "Cannot create listing for vehicle with status: " + vehicle.getStatus());
        }

        Listing listing = mapper.toEntity(request, hostId);
        listing = listingRepository.save(listing);

        log.info("Listing created: {} for vehicle {} by host {}", listing.getId(), request.vehicleId(), hostId);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional(readOnly = true)
    public Page<ListingSummaryResponse> listListings(UUID hostId, ListingStatus status, Pageable pageable) {
        Page<Listing> listings;
        if (status != null) {
            listings = listingRepository.findByHostIdAndStatus(hostId, status, pageable);
        } else {
            listings = listingRepository.findByHostId(hostId, pageable);
        }
        return mapper.toSummaryPage(listings);
    }

    @Transactional(readOnly = true)
    public ListingResponse getListing(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        List<ExtraResponse> extras = extraRepository.findByListingId(listingId).stream()
                .map(ExtraResponse::from).toList();
        return mapper.toResponse(listing, vehicle, extras);
    }

    @Transactional
    public ListingResponse updateListing(UUID listingId, UUID hostId, UpdateListingRequest request) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        ensureDraftMutable(listing);

        mapper.applyUpdate(listing, request);
        listing = listingRepository.save(listing);

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        List<ExtraResponse> extras = extraRepository.findByListingId(listingId).stream()
                .map(ExtraResponse::from).toList();

        log.info("Listing updated: {} by host {}", listingId, hostId);
        return mapper.toResponse(listing, vehicle, extras);
    }

    @Transactional(readOnly = true)
    public List<ExtraResponse> listExtras(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        return extraRepository.findByListingId(listing.getId()).stream()
                .map(ExtraResponse::from)
                .toList();
    }

    @Transactional
    public ExtraResponse createExtra(UUID listingId, UUID hostId, CreateExtraRequest request) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        ensureDraftMutable(listing);

        Extra extra = new Extra();
        extra.setListing(listing);
        extra.setName(request.name().trim());
        extra.setPricingType(request.pricingType());
        extra.setPrice(request.price());
        extra.setActive(true);

        Extra saved = extraRepository.save(extra);
        log.info("Extra created: {} for listing {} by host {}", saved.getId(), listingId, hostId);
        return ExtraResponse.from(saved);
    }

    @Transactional
    public ExtraResponse updateExtra(UUID listingId, UUID extraId, UUID hostId, UpdateExtraRequest request) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        ensureDraftMutable(listing);

        Extra extra = extraRepository.findByIdAndListingId(extraId, listingId)
                .orElseThrow(() -> new BusinessRuleException("EXTRA_NOT_FOUND", "Extra not found: " + extraId));

        if (request.name() != null) {
            String normalized = request.name().trim();
            if (normalized.isEmpty()) {
                throw new BusinessRuleException("VALIDATION_ERROR", "Extra name must not be blank");
            }
            extra.setName(normalized);
        }
        if (request.pricingType() != null) {
            extra.setPricingType(request.pricingType());
        }
        if (request.price() != null) {
            extra.setPrice(request.price());
        }
        if (request.active() != null) {
            extra.setActive(request.active());
        }

        Extra saved = extraRepository.save(extra);
        log.info("Extra updated: {} for listing {} by host {}", extraId, listingId, hostId);
        return ExtraResponse.from(saved);
    }

    @Transactional
    public void deleteExtra(UUID listingId, UUID extraId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        ensureDraftMutable(listing);

        Extra extra = extraRepository.findByIdAndListingId(extraId, listingId)
                .orElseThrow(() -> new BusinessRuleException("EXTRA_NOT_FOUND", "Extra not found: " + extraId));
        extra.setActive(false);
        extraRepository.save(extra);
        log.info("Extra soft-deleted: {} for listing {} by host {}", extraId, listingId, hostId);
    }

    private void ensureDraftMutable(Listing listing) {
        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new BusinessRuleException("LISTING_IMMUTABLE",
                    "Only DRAFT listings can be edited. Archive an ACTIVE listing, reactivate it to DRAFT, then resubmit.");
        }
    }

    @Transactional
    public ListingResponse submitListing(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.DRAFT) {
            throw new BusinessRuleException("ALREADY_SUBMITTED",
                    "Listing can only be submitted from DRAFT status");
        }

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE before listing can be submitted. Current status: " + vehicle.getStatus());
        }

        // Atomic UPDATE: only transitions DRAFT → PENDING_APPROVAL
        int updated = listingRepository.atomicSubmitListing(listingId);

        if (updated == 0) {
            throw new BusinessRuleException("ALREADY_SUBMITTED",
                    "Listing has already been submitted or is no longer in DRAFT status");
        }

        log.info("Listing submitted: {} by host {}", listingId, hostId);

        // Reload to get the updated entity
        listing = listingRepository.findById(listingId).orElseThrow();
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse archiveListing(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (!stateMachine.canTransition(listing.getStatus(), ListingStatus.ARCHIVED)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Cannot archive listing from status: " + listing.getStatus());
        }

        listing.setStatus(ListingStatus.ARCHIVED);
        listing = listingRepository.save(listing);

        log.info("Listing archived: {} by host {}", listingId, hostId);
        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse reactivateListing(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.ARCHIVED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Can only reactivate ARCHIVED listings");
        }

        listing.setStatus(ListingStatus.DRAFT);
        listing = listingRepository.save(listing);

        log.info("Listing reactivated: {} by host {}", listingId, hostId);
        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId()).orElse(null);
        return mapper.toResponse(listing, vehicle, List.of());
    }

    @Transactional
    public ListingResponse resumeListing(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findByIdAndHostId(listingId, hostId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        if (listing.getStatus() != ListingStatus.SUSPENDED) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                    "Only SUSPENDED listings can be resumed");
        }

        Vehicle vehicle = vehicleRepository.findById(listing.getVehicleId())
                .orElseThrow(() -> new BusinessRuleException("VEHICLE_NOT_FOUND", "Vehicle not found"));

        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new BusinessRuleException("VEHICLE_NOT_ACTIVE",
                    "Vehicle must be ACTIVE to resume listing");
        }

        boolean hasActiveListing = listingRepository.existsByVehicleIdAndStatus(vehicle.getId(), ListingStatus.ACTIVE);
        if (hasActiveListing) {
            throw new BusinessRuleException("ONE_ACTIVE_LISTING_PER_VEHICLE",
                    "Vehicle already has an ACTIVE listing");
        }

        if (availabilityRepository.countByListingId(listingId) == 0) {
            throw new BusinessRuleException("LISTING_NOT_PREVIOUSLY_ACTIVE",
                    "Only listings that were previously ACTIVE can be resumed");
        }

        listing.setStatus(ListingStatus.ACTIVE);
        listing = listingRepository.save(listing);

        log.info("Listing resumed: {} by host {}", listingId, hostId);
        return mapper.toResponse(listing, vehicle, List.of());
    }
}
