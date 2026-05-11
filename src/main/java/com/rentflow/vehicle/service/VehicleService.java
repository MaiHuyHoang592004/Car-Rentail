package com.rentflow.vehicle.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.VehicleArchiveNotAllowedException;
import com.rentflow.common.exception.VehicleNotFoundException;
import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.UpdateVehicleRequest;
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

    private final VehicleRepository vehicleRepository;
    private final ListingRepository listingRepository;
    private final VehicleStateMachine stateMachine;
    private final VehicleMapper mapper;
    private final EncryptionUtil encryptionUtil;

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
        return vehicles.map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID vehicleId, UUID hostId) {
        Vehicle vehicle = vehicleRepository.findByIdAndHostId(vehicleId, hostId)
                .orElseThrow(() -> new VehicleNotFoundException(vehicleId.toString()));
        return mapper.toResponse(vehicle);
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
        return mapper.toResponse(vehicle);
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

        List<Listing> listings = listingRepository.findAllByVehicleIdAndStatusNot(
                vehicleId, ListingStatus.ARCHIVED);

        for (Listing listing : listings) {
            listing.setStatus(ListingStatus.ARCHIVED);
            listingRepository.save(listing);
        }

        vehicle.setStatus(VehicleStatus.ARCHIVED);
        vehicleRepository.save(vehicle);

        log.info("Vehicle archived: {} by host {}", vehicleId, hostId);
    }

    private boolean hasActiveBookings(UUID vehicleId) {
        // TODO (Phase 4): Replace with bookings table check:
        //   SELECT EXISTS(SELECT 1 FROM bookings b
        //     JOIN listings l ON b.listing_id = l.id
        //     WHERE l.vehicle_id = :vehicleId
        //     AND b.status IN ('HELD', 'CONFIRMED', 'IN_PROGRESS'))
        //
        // For Phase 3, check availability_calendar for non-FREE rows.
        // This returns true if any date has HOLD/BOOKED status, which implies
        // an active booking exists. After Phase 4, update to query the bookings table directly.
        return listingRepository.existsNonArchivedListingsWithActiveAvailability(vehicleId);
    }

    private void propagateListingStateOnVehicleStatusChange(Vehicle vehicle,
            VehicleStatus fromStatus, VehicleStatus toStatus) {
        boolean transitioningToMaintenanceOrSuspended =
                toStatus == VehicleStatus.MAINTENANCE || toStatus == VehicleStatus.SUSPENDED;
        boolean transitioningFromActive = fromStatus == VehicleStatus.ACTIVE;

        if (transitioningToMaintenanceOrSuspended && transitioningFromActive) {
            int updated = listingRepository.updateStatusByVehicleIdAndStatusNot(
                    vehicle.getId(), ListingStatus.SUSPENDED, ListingStatus.ARCHIVED);
            log.info("Auto-suspended {} listings for vehicle {} -> {}",
                    updated, vehicle.getId(), toStatus);
        }
    }
}
