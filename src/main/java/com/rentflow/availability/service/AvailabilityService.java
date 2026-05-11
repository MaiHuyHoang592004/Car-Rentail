package com.rentflow.availability.service;

import com.rentflow.availability.dto.HostAvailabilityResponse;
import com.rentflow.availability.dto.HostDateStatus;
import com.rentflow.availability.dto.PublicAvailabilityResponse;
import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityStatus;
import com.rentflow.availability.repository.AvailabilityCalendarRepository;
import com.rentflow.common.exception.AccessDeniedException;
import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private static final int BATCH_SIZE = 100;
    private static final int DAYS_TO_GENERATE = 365;

    private final AvailabilityCalendarRepository availabilityRepository;
    private final ListingRepository listingRepository;
    private final Clock clock;

    @Transactional
    public int generateForListing(UUID listingId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = today.plusDays(DAYS_TO_GENERATE - 1);

        log.info("Generating {} days of availability for listing {} ({} to {})",
                DAYS_TO_GENERATE, listingId, today, endDate);

        List<AvailabilityCalendar> batch = new ArrayList<>(BATCH_SIZE);
        int totalInserted = 0;

        LocalDate current = today;
        while (!current.isAfter(endDate)) {
            if (!availabilityRepository.existsByListingIdAndAvailableDate(listingId, current)) {
                batch.add(new AvailabilityCalendar(listingId, current));
            }

            if (batch.size() >= BATCH_SIZE) {
                availabilityRepository.saveAll(batch);
                totalInserted += batch.size();
                batch.clear();
            }

            current = current.plusDays(1);
        }

        if (!batch.isEmpty()) {
            availabilityRepository.saveAll(batch);
            totalInserted += batch.size();
        }

        log.info("Generated {} availability rows for listing {}", totalInserted, listingId);
        return totalInserted;
    }

    @Transactional
    public int blockDates(UUID listingId, LocalDate from, LocalDate to) {
        int updated = availabilityRepository.updateStatusByDateRange(
                listingId, from, to, AvailabilityStatus.BLOCKED);
        log.info("Blocked {} dates for listing {} ({} to {})", updated, listingId, from, to);
        return updated;
    }

    @Transactional
    public int unblockDates(UUID listingId, LocalDate from, LocalDate to) {
        int updated = availabilityRepository.updateStatusByDateRange(
                listingId, from, to, AvailabilityStatus.FREE);
        log.info("Unblocked {} dates for listing {} ({} to {})", updated, listingId, from, to);
        return updated;
    }

    @Transactional(readOnly = true)
    public PublicAvailabilityResponse getPublicAvailability(UUID listingId, LocalDate from, LocalDate to) {
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (from.plusDays(90).isBefore(to)) {
            throw new IllegalArgumentException("date range must not exceed 90 days");
        }

        List<AvailabilityCalendar> rows = availabilityRepository.findByListingIdAndAvailableDateRange(
                listingId, from, to);

        Map<LocalDate, String> availability = new LinkedHashMap<>();
        for (AvailabilityCalendar row : rows) {
            availability.put(row.getAvailableDate(), toPublicStatus(row.getStatus()));
        }

        return new PublicAvailabilityResponse(listingId, availability);
    }

    private String toPublicStatus(AvailabilityStatus status) {
        return switch (status) {
            case FREE -> "FREE";
            case BLOCKED -> "BLOCKED";
            case HOLD, BOOKED -> "UNAVAILABLE";
        };
    }

    public void checkOwnership(UUID listingId, UUID hostId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));
        if (!listing.getHostId().equals(hostId)) {
            throw new AccessDeniedException();
        }
    }

    @Transactional(readOnly = true)
    public HostAvailabilityResponse getHostAvailability(UUID listingId, LocalDate from, LocalDate to, UUID hostId) {
        checkOwnership(listingId, hostId);

        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
        if (from.plusDays(90).isBefore(to)) {
            throw new IllegalArgumentException("date range must not exceed 90 days");
        }

        List<AvailabilityCalendar> rows = availabilityRepository.findByListingIdAndAvailableDateRange(
                listingId, from, to);

        Map<LocalDate, HostDateStatus> dateMap = new LinkedHashMap<>();
        for (AvailabilityCalendar row : rows) {
            if (shouldShowInHostView(row)) {
                dateMap.put(row.getAvailableDate(), toHostDateStatus(row));
            }
        }

        return new HostAvailabilityResponse(listingId, from, to, new ArrayList<>(dateMap.values()));
    }

    @Transactional
    public int blockDates(UUID listingId, List<LocalDate> dates, UUID hostId) {
        checkOwnership(listingId, hostId);
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("dates must not be empty");
        }
        List<LocalDate> normalized = dates.stream().distinct().sorted().toList();

        List<LocalDate> conflicts = availabilityRepository.findConflictingDates(
                listingId, normalized,
                List.of(AvailabilityStatus.HOLD, AvailabilityStatus.BOOKED));
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleException("AVAILABILITY_CONFLICT",
                    "Cannot block dates: " + conflicts);
        }

        int updated = availabilityRepository.updateStatusByDates(
                listingId, normalized,
                AvailabilityStatus.BLOCKED, AvailabilityStatus.FREE);
        log.info("Blocked {} dates for listing {}", updated, listingId);
        return updated;
    }

    @Transactional
    public int unblockDates(UUID listingId, List<LocalDate> dates, UUID hostId) {
        checkOwnership(listingId, hostId);
        if (dates == null || dates.isEmpty()) {
            throw new IllegalArgumentException("dates must not be empty");
        }
        List<LocalDate> normalized = dates.stream().distinct().sorted().toList();

        int updated = availabilityRepository.updateStatusByDates(
                listingId, normalized,
                AvailabilityStatus.FREE, AvailabilityStatus.BLOCKED);
        log.info("Unblocked {} dates for listing {}", updated, listingId);
        return updated;
    }

    private HostDateStatus toHostDateStatus(AvailabilityCalendar row) {
        return new HostDateStatus(
                row.getAvailableDate(),
                row.getStatus(),
                row.getBookingId() == null ? null : List.of(row.getBookingId()),
                row.getHoldExpiresAt()
        );
    }

    private boolean shouldShowInHostView(AvailabilityCalendar row) {
        if (row.getStatus() != AvailabilityStatus.FREE) {
            return true;
        }
        return row.getCreatedAt() != null
                && row.getUpdatedAt() != null
                && row.getUpdatedAt().isAfter(row.getCreatedAt());
    }
}
