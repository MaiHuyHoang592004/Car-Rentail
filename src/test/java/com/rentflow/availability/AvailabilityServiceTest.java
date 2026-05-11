package com.rentflow.availability;

import com.rentflow.availability.dto.PublicAvailabilityResponse;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.availability.service.AvailabilityService;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AvailabilityService")
class AvailabilityServiceTest {

    @Mock
    private AvailabilityCalendarRepository availabilityRepository;

    @Mock
    private ListingRepository listingRepository;

    private AvailabilityService availabilityService;

    private static final UUID LISTING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID HOST_ID    = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-05-11T00:00:00Z"),
                ZoneOffset.UTC);
        availabilityService = new AvailabilityService(
                availabilityRepository, listingRepository, fixedClock);

        Listing listing = new Listing();
        listing.setHostId(HOST_ID);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
    }

    @Nested
    @DisplayName("blockDates(UUID, List<LocalDate>, UUID)")
    class BlockDates {

        @Test
        @DisplayName("with HOLD date — throws AVAILABILITY_CONFLICT")
        void blockDates_withHoldDate_throwsConflict() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            List<LocalDate> dates = List.of(date);

            when(availabilityRepository.findConflictingDates(
                    eq(LISTING_ID), any(),
                    argThat(statuses ->
                            statuses.contains(AvailabilityStatus.HOLD) &&
                            statuses.contains(AvailabilityStatus.BOOKED))))
                    .thenReturn(dates);

            assertThatThrownBy(() -> availabilityService.blockDates(LISTING_ID, dates, HOST_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "AVAILABILITY_CONFLICT");

            verify(availabilityRepository, never())
                    .updateStatusByDates(any(), any(), any(), any());
        }

        @Test
        @DisplayName("with BOOKED date — throws AVAILABILITY_CONFLICT")
        void blockDates_withBookedDate_throwsConflict() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            List<LocalDate> dates = List.of(date);

            when(availabilityRepository.findConflictingDates(
                    eq(LISTING_ID), any(),
                    argThat(statuses ->
                            statuses.contains(AvailabilityStatus.HOLD) &&
                            statuses.contains(AvailabilityStatus.BOOKED))))
                    .thenReturn(dates);

            assertThatThrownBy(() -> availabilityService.blockDates(LISTING_ID, dates, HOST_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasFieldOrPropertyWithValue("code", "AVAILABILITY_CONFLICT");
        }

        @Test
        @DisplayName("with only FREE dates — updates and returns count")
        void blockDates_withFreeDates_succeeds() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            List<LocalDate> dates = List.of(date);

            when(availabilityRepository.findConflictingDates(any(), any(), any()))
                    .thenReturn(List.of());
            when(availabilityRepository.updateStatusByDates(
                    eq(LISTING_ID), any(),
                    eq(AvailabilityStatus.BLOCKED), eq(AvailabilityStatus.FREE)))
                    .thenReturn(1);

            int result = availabilityService.blockDates(LISTING_ID, dates, HOST_ID);

            assertThat(result).isEqualTo(1);
            verify(availabilityRepository).updateStatusByDates(
                    eq(LISTING_ID), eq(dates),
                    eq(AvailabilityStatus.BLOCKED), eq(AvailabilityStatus.FREE));
        }

        @Test
        @DisplayName("with duplicate dates — deduplicates before update")
        void blockDates_withDuplicateDates_deduplicatesBeforeUpdate() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            // Same date passed three times
            List<LocalDate> datesWithDupes = List.of(date, date, date);

            when(availabilityRepository.findConflictingDates(any(), any(), any()))
                    .thenReturn(List.of());
            when(availabilityRepository.updateStatusByDates(
                    eq(LISTING_ID), any(),
                    eq(AvailabilityStatus.BLOCKED), eq(AvailabilityStatus.FREE)))
                    .thenReturn(1);

            availabilityService.blockDates(LISTING_ID, datesWithDupes, HOST_ID);

            verify(availabilityRepository).updateStatusByDates(
                    eq(LISTING_ID),
                    argThat(list -> list.size() == 1 && list.get(0).equals(date)),
                    eq(AvailabilityStatus.BLOCKED), eq(AvailabilityStatus.FREE));
        }

        @Test
        @DisplayName("with empty dates — throws IllegalArgumentException")
        void blockDates_withEmptyDates_throwsValidationError() {
            List<LocalDate> empty = List.of();

            assertThatThrownBy(() -> availabilityService.blockDates(LISTING_ID, empty, HOST_ID))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("dates must not be empty");

            verify(availabilityRepository, never()).findConflictingDates(any(), any(), any());
            verify(availabilityRepository, never()).updateStatusByDates(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("unblockDates(UUID, List<LocalDate>, UUID)")
    class UnblockDates {

        @Test
        @DisplayName("with BLOCKED date — updates and returns count")
        void unblockDates_withBlockedDate_succeeds() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            List<LocalDate> dates = List.of(date);

            when(availabilityRepository.updateStatusByDates(
                    eq(LISTING_ID), any(),
                    eq(AvailabilityStatus.FREE), eq(AvailabilityStatus.BLOCKED)))
                    .thenReturn(1);

            int result = availabilityService.unblockDates(LISTING_ID, dates, HOST_ID);

            assertThat(result).isEqualTo(1);
            verify(availabilityRepository).updateStatusByDates(
                    eq(LISTING_ID), eq(dates),
                    eq(AvailabilityStatus.FREE), eq(AvailabilityStatus.BLOCKED));
        }

        @Test
        @DisplayName("with FREE date — skips gracefully, returns zero")
        void unblockDates_withFreeDate_skipsGracefully() {
            LocalDate date = LocalDate.of(2026, 5, 15);
            List<LocalDate> dates = List.of(date);

            when(availabilityRepository.updateStatusByDates(any(), any(), any(), any()))
                    .thenReturn(0);

            int result = availabilityService.unblockDates(LISTING_ID, dates, HOST_ID);

            assertThat(result).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getPublicAvailability")
    class GetPublicAvailability {

        @Test
        @DisplayName("with HOLD row — maps to UNAVAILABLE")
        void getPublicAvailability_withHoldRow_returnsUnavailable() {
            AvailabilityCalendar holdRow = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 5, 15));
            holdRow.setStatus(AvailabilityStatus.HOLD);

            when(availabilityRepository.findByListingIdAndAvailableDateRange(
                    eq(LISTING_ID), any(), any()))
                    .thenReturn(List.of(holdRow));

            PublicAvailabilityResponse result = availabilityService.getPublicAvailability(
                    LISTING_ID, LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 16));

            assertThat(result.availability().get(LocalDate.of(2026, 5, 15))).isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("with BOOKED row — does not expose bookingId")
        void getPublicAvailability_withBookedRow_doesNotExposeBookingId() {
            AvailabilityCalendar bookedRow = new AvailabilityCalendar(LISTING_ID, LocalDate.of(2026, 5, 15));
            bookedRow.setStatus(AvailabilityStatus.BOOKED);
            bookedRow.setBookingId(UUID.randomUUID());

            when(availabilityRepository.findByListingIdAndAvailableDateRange(any(), any(), any()))
                    .thenReturn(List.of(bookedRow));

            PublicAvailabilityResponse result = availabilityService.getPublicAvailability(
                    LISTING_ID, LocalDate.of(2026, 5, 15), LocalDate.of(2026, 5, 16));

            // PublicAvailabilityResponse.availability() is Map<LocalDate, String>
            // bookingId is never included; only the status string is returned
            assertThat(result.availability().get(LocalDate.of(2026, 5, 15))).isEqualTo("UNAVAILABLE");
            // Confirm the entity field is set but not surfaced in the response map
            assertThat(bookedRow.getBookingId()).isNotNull();
        }
    }
}
