package com.rentflow.booking.repository;

import com.rentflow.booking.entity.BookingTimelineEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BookingTimelineEntryRepository extends JpaRepository<BookingTimelineEntry, UUID> {
}
