package com.rentflow.listing;

import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.auth.repository.AuthUserRepository;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.event.ListingApprovedEvent;
import com.rentflow.listing.mapper.ListingMapper;
import com.rentflow.listing.repository.ExtraRepository;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.listing.service.AdminListingService;
import com.rentflow.listing.service.ListingStateMachine;
import com.rentflow.outbox.service.OutboxService;
import com.rentflow.user.repository.UserProfileRepository;
import com.rentflow.vehicle.entity.*;
import com.rentflow.vehicle.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneActiveListingPerVehicleTest {

    @Mock private ListingRepository listingRepository;
    @Mock private VehicleRepository vehicleRepository;
    @Mock private AvailabilityCalendarRepository availabilityRepository;
    @Mock private ExtraRepository extraRepository;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private AuthUserRepository authUserRepository;
    @Mock private OutboxService outboxService;
    @Spy private ListingStateMachine stateMachine = new ListingStateMachine();
    private ListingMapper listingMapper = new ListingMapper();
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminListingService adminListingService;
    private UUID listingId;
    private UUID vehicleId;
    private UUID hostId;
    private Listing listing;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        adminListingService = new AdminListingService(
            listingRepository, vehicleRepository, availabilityRepository,
            eventPublisher, stateMachine, listingMapper,
            extraRepository, userProfileRepository, authUserRepository,
            outboxService, objectMapper);

        listingId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();
        hostId = UUID.randomUUID();

        listing = new Listing();
        listing.setId(listingId);
        listing.setVehicleId(vehicleId);
        listing.setHostId(hostId);
        listing.setTitle("My Car");
        listing.setCity("Hanoi");
        listing.setBasePricePerDay(BigDecimal.valueOf(500000));
        listing.setCurrency("VND");
        listing.setStatus(ListingStatus.PENDING_APPROVAL);

        vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setHostId(hostId);
        vehicle.setCategory(VehicleCategory.SEDAN);
        vehicle.setMake("Toyota");
        vehicle.setModel("Camry");
        vehicle.setManufactureYear(2020);
        vehicle.setTransmission(TransmissionType.AUTO);
        vehicle.setFuelType(FuelType.GASOLINE);
        vehicle.setSeats(5);
        vehicle.setStatus(VehicleStatus.ACTIVE);
    }

    @Test
    void approveListing_succeedsWhenNoOtherActiveListing() {
        when(listingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(listingRepository.existsByVehicleIdAndStatus(vehicleId, ListingStatus.ACTIVE)).thenReturn(false);
        when(listingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = adminListingService.approveListing(listingId);

        assertThat(result.status()).isEqualTo(ListingStatus.ACTIVE);
        verify(eventPublisher).publishEvent(new ListingApprovedEvent(listingId));
    }

    @Test
    void approveListing_failsWhenAnotherActiveListingExists() {
        when(listingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(listingRepository.existsByVehicleIdAndStatus(vehicleId, ListingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> adminListingService.approveListing(listingId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void approveListing_failsWhenVehicleNotActive() {
        vehicle.setStatus(VehicleStatus.MAINTENANCE);
        when(listingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> adminListingService.approveListing(listingId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void approveListing_failsWhenListingNotPendingApproval() {
        listing.setStatus(ListingStatus.DRAFT);
        when(listingRepository.findByIdForUpdate(listingId)).thenReturn(Optional.of(listing));
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> adminListingService.approveListing(listingId))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("PENDING_APPROVAL");
    }
}
