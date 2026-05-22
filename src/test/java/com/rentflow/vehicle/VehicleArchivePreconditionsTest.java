package com.rentflow.vehicle;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.VehicleArchiveNotAllowedException;
import com.rentflow.common.util.EncryptionUtil;
import com.rentflow.booking.entity.BookingStatus;
import com.rentflow.booking.repository.BookingRepository;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.dto.CreateVehicleRequest;
import com.rentflow.vehicle.dto.VehicleResponse;
import com.rentflow.vehicle.entity.*;
import com.rentflow.vehicle.mapper.VehicleMapper;
import com.rentflow.vehicle.repository.VehicleRepository;
import com.rentflow.vehicle.service.VehicleService;
import com.rentflow.vehicle.service.VehicleStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleArchivePreconditionsTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Spy
    private VehicleStateMachine stateMachine = new VehicleStateMachine();

    @Mock
    private EncryptionUtil encryptionUtil;

    private VehicleMapper mapper;

    private VehicleService vehicleService;

    private UUID hostId;
    private UUID vehicleId;
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        mapper = new VehicleMapper(encryptionUtil);
        vehicleService = new VehicleService(
            vehicleRepository, listingRepository, bookingRepository, stateMachine, mapper, encryptionUtil);

        hostId = UUID.randomUUID();
        vehicleId = UUID.randomUUID();

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
    void archiveVehicle_succeedsWhenNoActiveListings() {
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.existsActiveBookingsForVehicle(
                vehicleId,
                List.of(BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS)))
                .thenReturn(false);
        when(listingRepository.updateStatusByVehicleIdAndStatusNot(
                vehicleId, ListingStatus.ARCHIVED, ListingStatus.ARCHIVED))
            .thenReturn(0);
        when(vehicleRepository.save(any())).thenReturn(vehicle);

        assertThatCode(() -> vehicleService.archiveVehicle(vehicleId, hostId))
            .doesNotThrowAnyException();

        verify(vehicleRepository).save(any());
    }

    @Test
    void archiveVehicle_failsWhenActiveListingsExist() {
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));
        when(bookingRepository.existsActiveBookingsForVehicle(
                vehicleId,
                List.of(BookingStatus.HELD, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS)))
                .thenReturn(true);

        assertThatThrownBy(() -> vehicleService.archiveVehicle(vehicleId, hostId))
            .isInstanceOf(VehicleArchiveNotAllowedException.class);
    }

    @Test
    void archiveVehicle_failsWhenVehicleAlreadyArchived() {
        vehicle.setStatus(VehicleStatus.ARCHIVED);
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.archiveVehicle(vehicleId, hostId))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void archiveVehicle_failsWhenVehicleNotOwnedByHost() {
        UUID differentHost = UUID.randomUUID();
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.of(vehicle));

        assertThatThrownBy(() -> vehicleService.archiveVehicle(vehicleId, differentHost))
            .isInstanceOf(com.rentflow.common.exception.VehicleNotFoundException.class);
    }

    @Test
    void archiveVehicle_failsWhenVehicleNotFound() {
        when(vehicleRepository.findByIdForUpdate(vehicleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.archiveVehicle(vehicleId, hostId))
            .isInstanceOf(com.rentflow.common.exception.VehicleNotFoundException.class);
    }
}
