package com.rentflow.vehicle.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.VehicleArchiveNotAllowedException;
import com.rentflow.common.exception.VehicleNotFoundException;
import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.booking.service.VehicleBookingPort;
import com.rentflow.file.service.FileService;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.listing.service.VehicleListingPort;
import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.UpdateVehicleRequest;
import com.rentflow.vehicle.dto.VehicleArchivePreviewResponse;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.mapper.VehicleMapper;
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
public class VehicleService {

    private static final String VEHICLE_STATUS_CHANGE_SUSPENSION_SOURCE = "VEHICLE_STATUS_CHANGE";

    private final VehicleRepository vehicleRepository;
    private final VehicleListingPort listingPort;
    private final VehicleBookingPort bookingPort;
    private final VehicleStateMachine stateMachine;
    private final VehicleMapper mapper;
    private final EncryptionUtil encryptionUtil;
    private final FileService fileService;
    private final ListingRepository listingRepository;

    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request, UUID hostId) {
        String plateHash = encryptionUtil.hash(request.plateNumber());
        String encryptedPlate = encryptionUtil.encrypt(request.plateNumber());
        String encryptedVin = request.vin() != null ? encryptionUtil.encrypt(request.vin()) : null;
        String vinHash = request.vin() != null ? encryptionUtil.hash(request.vin()) : null;

        Vehicle vehicle = mapper.toEntity(request, encryptedPlate, plateHash, encryptedVin, vinHash);
        vehicle.setHostId(hostId);

        vehicle = vehicleRepository.save(vehicle);
        log.info("Vehicle created: {} for host {}", vehicle.getId(), hostId);
        return mapper.toResponse(vehicle);
    }

    @Transactional(readOnly = true)
    public Page<VehicleResponse> listVehicles(UUID hostId, VehicleStatus status, Pageable pageable) {
        Page<Vehicle> vehicles;
        if (status != null) {
            vehicles = vehicleRepository.findByHostIdAndStatus(hostId, status, pageable);
        } else {
            vehicles = vehicleRepository.findByHostId(hostId, pageable);
        }
        return vehicles.map(vehicle -> mapper.toResponse(vehicle, photosForVehicle(vehicle.getId())));
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID vehicleId, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findByIdAndHostId(vehicleId, hostId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));
        return mapper.toResponse(vehicle, photosForVehicle(vehicleId));
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID vehicleId, UUID hostId, UpdateVehicleRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));

        if (!vehicle.getHostId().equals(hostId)) {
            throw new VehicleNotFoundException(vehicleId.toString());
        }

        VehicleStatus previousStatus = vehicle.getStatus();
        mapper.applyUpdate(vehicle, request);

        if (request.status() != null && request.status() != previousStatus) {
            stateMachine.validateTransition(previousStatus, request.status());
            propagateListingStateOnVehicleStatusChange(vehicle, previousStatus, request.status());
        }

        vehicle = vehicleRepository.save(vehicle);
        log.info("Vehicle updated: {} by host {}", vehicleId, hostId);
        return mapper.toResponse(vehicle, photosForVehicle(vehicleId));
    }

    @Transactional
    public void archiveVehicle(UUID vehicleId, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));

        if (!vehicle.getHostId().equals(hostId)) {
            throw new VehicleNotFoundException(vehicleId.toString());
        }

        if (vehicle.getStatus() == VehicleStatus.ARCHIVED) {
            throw new BusinessRuleException("ALREADY_ARCHIVED",
                "Vehicle is already archived");
        }

        if (hasActiveBookings(vehicleId)) {
            throw new VehicleArchiveNotAllowedException(
                "Vehicle cannot be archived: active bookings exist for related listings");
        }

        listingPort.archiveListings(vehicleId);

        vehicle.setStatus(VehicleStatus.ARCHIVED);
        vehicleRepository.save(vehicle);

        log.info("Vehicle archived: {} by host {}", vehicleId, hostId);
    }

    @Transactional(readOnly = true)
    public VehicleArchivePreviewResponse getArchivePreview(UUID vehicleId, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findByIdAndHostId(vehicleId, hostId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));

        List<VehicleArchivePreviewResponse.AffectedListing> affectedListings =
                listingRepository.findAllByVehicleIdAndStatusNot(vehicleId, ListingStatus.ARCHIVED).stream()
                        .map(this::toAffectedListing)
                        .toList();
        boolean hasActiveBookings = hasActiveBookings(vehicleId);
        boolean alreadyArchived = vehicle.getStatus() == VehicleStatus.ARCHIVED;
        boolean archiveAllowed = !hasActiveBookings && !alreadyArchived;
        String blockingReason = null;
        if (alreadyArchived) {
            blockingReason = "Xe da o trang thai luu kho.";
        } else if (hasActiveBookings) {
            blockingReason = "Xe dang co booking HOLD/BOOKED o cac listing lien quan.";
        }

        return new VehicleArchivePreviewResponse(
                vehicleId,
                affectedListings,
                hasActiveBookings,
                archiveAllowed,
                blockingReason);
    }

    private boolean hasActiveBookings(UUID vehicleId) {
        return bookingPort.hasActiveBookings(vehicleId);
    }

    private List<VehicleResponse.Photo> photosForVehicle(UUID vehicleId) {
        return fileService.listVehiclePhotos(vehicleId).stream()
                .map(photo -> new VehicleResponse.Photo(
                        photo.id(),
                        photo.fileId(),
                        photo.primary(),
                        photo.displayOrder(),
                        photo.signedUrl()))
                .toList();
    }

    private VehicleArchivePreviewResponse.AffectedListing toAffectedListing(Listing listing) {
        return new VehicleArchivePreviewResponse.AffectedListing(
                listing.getId(),
                listing.getTitle(),
                listing.getStatus());
    }

    private void propagateListingStateOnVehicleStatusChange(Vehicle vehicle,
            VehicleStatus fromStatus, VehicleStatus toStatus) {
        boolean transitioningToMaintenanceOrSuspended =
                toStatus == VehicleStatus.MAINTENANCE || toStatus == VehicleStatus.SUSPENDED;
        boolean transitioningFromActive = fromStatus == VehicleStatus.ACTIVE;

        if (transitioningToMaintenanceOrSuspended && transitioningFromActive) {
            int updated = listingPort.suspendListings(
                    vehicle.getId(),
                    "Vehicle moved to " + toStatus.name(),
                    VEHICLE_STATUS_CHANGE_SUSPENSION_SOURCE);
            log.info("Auto-suspended {} listings for vehicle {} -> {}",
                    updated, vehicle.getId(), toStatus);
        }
    }
}
