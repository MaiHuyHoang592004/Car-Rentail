package com.rentflow.availability.repository;

import com.rentflow.availability.entity.AvailabilityCalendar;
import com.rentflow.availability.entity.AvailabilityId;
import com.rentflow.availability.entity.AvailabilityStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AvailabilityCalendarRepository extends JpaRepository<AvailabilityCalendar, AvailabilityId> {

    @Query("SELECT ac FROM AvailabilityCalendar ac " +
           "WHERE ac.listingId = :listingId " +
           "AND ac.availableDate BETWEEN :from AND :to " +
           "ORDER BY ac.availableDate ASC")
    List<AvailabilityCalendar> findByListingIdAndAvailableDateBetweenOrderByAvailableDateAsc(
            @Param("listingId") UUID listingId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ac FROM AvailabilityCalendar ac " +
           "WHERE ac.listingId = :listingId " +
           "AND ac.availableDate BETWEEN :from AND :to " +
           "ORDER BY ac.availableDate ASC")
    List<AvailabilityCalendar> findByListingIdAndDateRangeForUpdate(
            @Param("listingId") UUID listingId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT ac FROM AvailabilityCalendar ac " +
           "WHERE ac.listingId = :listingId " +
           "AND ac.availableDate >= :from " +
           "AND ac.availableDate < :to " +
           "ORDER BY ac.availableDate ASC")
    List<AvailabilityCalendar> findByListingIdAndAvailableDateRange(
            @Param("listingId") UUID listingId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT COUNT(ac) FROM AvailabilityCalendar ac WHERE ac.listingId = :listingId")
    long countByListingId(@Param("listingId") UUID listingId);

    boolean existsByListingIdAndAvailableDate(UUID listingId, LocalDate availableDate);

    @Modifying
    @Query("UPDATE AvailabilityCalendar ac SET ac.status = :status " +
           "WHERE ac.listingId = :listingId " +
           "AND ac.availableDate BETWEEN :from AND :to " +
           "AND ac.status = 'FREE'")
    int updateStatusByDateRange(
            @Param("listingId") UUID listingId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") AvailabilityStatus status);

    @Query("SELECT ac.availableDate FROM AvailabilityCalendar ac " +
           "WHERE ac.listingId = :listingId AND ac.availableDate IN :dates " +
           "AND ac.status IN :statuses " +
           "ORDER BY ac.availableDate ASC")
    List<LocalDate> findConflictingDates(
            @Param("listingId") UUID listingId,
            @Param("dates") List<LocalDate> dates,
            @Param("statuses") List<AvailabilityStatus> statuses);

    @Modifying
    @Query("UPDATE AvailabilityCalendar ac SET ac.status = :targetStatus " +
           "WHERE ac.listingId = :listingId AND ac.availableDate IN :dates " +
           "AND ac.status = :currentStatus")
    int updateStatusByDates(
            @Param("listingId") UUID listingId,
            @Param("dates") List<LocalDate> dates,
            @Param("targetStatus") AvailabilityStatus targetStatus,
            @Param("currentStatus") AvailabilityStatus currentStatus);
}
