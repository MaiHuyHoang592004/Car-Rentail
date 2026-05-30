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

    private static final int DAYS_TO_GENERATE = 365;
    private static final int MAX_EXTEND_DAYS = 730;

    private final AvailabilityCalendarRepository availabilityRepository;
    private final ListingRepository listingRepository;
    private final Clock clock;

    @Transactional
    public int generateForListing(UUID listingId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate endDate = today.plusDays(DAYS_TO_GENERATE - 1);

        int inserted = availabilityRepository.insertAvailabilityRange(listingId, today, endDate);
        log.info("Generated {} availability rows for listing {} ({} to {})",
                inserted, listingId, today, endDate);
        return inserted;
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

    @Transactional
    public int blockDateRange(UUID listingId, LocalDate from, LocalDate to, UUID hostId) {
        checkOwnership(listingId, hostId);
        validateInclusiveRange(from, to);

        List<AvailabilityCalendar> rows = availabilityRepository
                .findByListingIdAndAvailableDateBetweenOrderByAvailableDateAsc(listingId, from, to);
        List<LocalDate> conflicts = rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.HOLD || row.getStatus() == AvailabilityStatus.BOOKED)
                .map(AvailabilityCalendar::getAvailableDate)
                .toList();
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleException("AVAILABILITY_CONFLICT",
                    "Cannot block range because some dates are reserved: " + summarizeDates(conflicts));
        }

        List<LocalDate> freeDates = rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.FREE)
                .map(AvailabilityCalendar::getAvailableDate)
                .toList();
        if (freeDates.isEmpty()) {
            return 0;
        }

        int updated = availabilityRepository.updateStatusByDates(
                listingId,
                freeDates,
                AvailabilityStatus.BLOCKED,
                AvailabilityStatus.FREE);
        log.info("Blocked {} dates for listing {} ({} to {})", updated, listingId, from, to);
        return updated;
    }

    @Transactional
    public int unblockDateRange(UUID listingId, LocalDate from, LocalDate to, UUID hostId) {
        checkOwnership(listingId, hostId);
        validateInclusiveRange(from, to);

        List<AvailabilityCalendar> rows = availabilityRepository
                .findByListingIdAndAvailableDateBetweenOrderByAvailableDateAsc(listingId, from, to);
        List<LocalDate> conflicts = rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.HOLD || row.getStatus() == AvailabilityStatus.BOOKED)
                .map(AvailabilityCalendar::getAvailableDate)
                .toList();
        if (!conflicts.isEmpty()) {
            throw new BusinessRuleException("AVAILABILITY_CONFLICT",
                    "Cannot unblock range because some dates are reserved: " + summarizeDates(conflicts));
        }

        List<LocalDate> blockedDates = rows.stream()
                .filter(row -> row.getStatus() == AvailabilityStatus.BLOCKED)
                .map(AvailabilityCalendar::getAvailableDate)
                .toList();
        if (blockedDates.isEmpty()) {
            return 0;
        }

        int updated = availabilityRepository.updateStatusByDates(
                listingId,
                blockedDates,
                AvailabilityStatus.FREE,
                AvailabilityStatus.BLOCKED);
        log.info("Unblocked {} dates for listing {} ({} to {})", updated, listingId, from, to);
        return updated;
    }

    @Transactional
    public int extendAvailability(UUID listingId, LocalDate throughDate, UUID hostId) {
        checkOwnership(listingId, hostId);
        if (throughDate == null) {
            throw new IllegalArgumentException("throughDate is required");
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate maxAllowedDate = today.plusDays(MAX_EXTEND_DAYS);
        if (throughDate.isAfter(maxAllowedDate)) {
            throw new BusinessRuleException("AVAILABILITY_EXTEND_LIMIT",
                    "throughDate must be on or before " + maxAllowedDate);
        }

        LocalDate startDate = availabilityRepository.findMaxAvailableDateByListingId(listingId)
                .map(date -> date.plusDays(1))
                .orElse(today);
        if (throughDate.isBefore(startDate)) {
            return 0;
        }

        int inserted = availabilityRepository.insertAvailabilityRange(listingId, startDate, throughDate);
        log.info("Extended {} availability dates for listing {} through {}", inserted, listingId, throughDate);
        return inserted;
    }

    private void validateInclusiveRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
    }

    private String summarizeDates(List<LocalDate> dates) {
        if (dates.size() <= 5) {
            return dates.toString();
        }
        return dates.subList(0, 5) + " and " + (dates.size() - 5) + " more";
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
