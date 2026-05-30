package com.rentflow.listing;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.mapper.ListingMapper;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.listing.service.ListingService;
import com.rentflow.listing.service.ListingStateMachine;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.vehicle.entity.VehicleStatus;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingServiceResumeTest {

    @Mock
    private ListingRepository listingRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private ExtraRepository extraRepository;
    @Mock
    private AvailabilityCalendarRepository availabilityRepository;

    private ListingService listingService;
    private UUID hostId;
    private UUID listingId;
    private UUID vehicleId;

    @BeforeEach
    void setUp() {
        listingService = new ListingService(
                listingRepository,
                vehicleRepository,
                extraRepository,
                new ListingMapper(),
                new ListingStateMachine(),
                availabilityRepository);
        hostId = UUID.randomUUID();
        listingId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
    }

    @Test
    void resumeListing_succeedsWhenVehicleActiveAndNoOtherActiveListing() {
        Listing listing = suspendedListing();
        Vehicle vehicle = activeVehicle();

        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(listingRepository.existsByVehicleIdAndStatus(vehicleId, ListingStatus.ACTIVE)).thenReturn(false);
        when(availabilityRepository.countByListingId(listingId)).thenReturn(365L);
        when(listingRepository.save(any(Listing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListingResponse response = listingService.resumeListing(listingId, hostId);

        assertThat(response.status()).isEqualTo(ListingStatus.ACTIVE);
    }

    @Test
    void resumeListing_rejectsWhenVehicleNotActive() {
        Listing listing = suspendedListing();
        Vehicle vehicle = activeVehicle();
        vehicle.setStatus(VehicleStatus.SUSPENDED);

        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> listingService.resumeListing(listingId, hostId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Vehicle must be ACTIVE");
    }

    @Test
    void resumeListing_rejectsWhenAnotherActiveListingExists() {
        Listing listing = suspendedListing();
        Vehicle vehicle = activeVehicle();

        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(listingRepository.existsByVehicleIdAndStatus(vehicleId, ListingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> listingService.resumeListing(listingId, hostId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ACTIVE listing");
    }

    @Test
    void resumeListing_rejectsSuspendedListingThatWasNeverActive() {
        Listing listing = suspendedListing();
        Vehicle vehicle = activeVehicle();

        when(listingRepository.findByIdAndHostId(listingId, hostId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(vehicle));
        when(listingRepository.existsByVehicleIdAndStatus(vehicleId, ListingStatus.ACTIVE)).thenReturn(false);
        when(availabilityRepository.countByListingId(listingId)).thenReturn(0L);

        assertThatThrownBy(() -> listingService.resumeListing(listingId, hostId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("previously ACTIVE");
    }

    private Listing suspendedListing() {
        Listing listing = new Listing();
        listing.setId(listingId);
        listing.setHostId(hostId);
        listing.setVehicleId(vehicleId);
        listing.setStatus(ListingStatus.SUSPENDED);
        listing.setTitle("Listing A");
        listing.setCity("Hanoi");
        listing.setCurrency("VND");
        listing.setExtras(List.of());
        return listing;
    }

    private Vehicle activeVehicle() {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setHostId(hostId);
        vehicle.setStatus(VehicleStatus.ACTIVE);
        return vehicle;
    }
}
