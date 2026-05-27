package com.rentflow.booking.service;

import com.rentflow.booking.entity.BookingTimelineEntry;
import com.rentflow.booking.repository.BookingTimelineEntryRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class DefaultBookingTimelineService implements BookingTimelineService {

    private final BookingTimelineEntryRepository repository;

    public DefaultBookingTimelineService(BookingTimelineEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(UUID bookingId, String eventType, UUID actorUserId, String actorType, String payloadJson) {
        BookingTimelineEntry entry = new BookingTimelineEntry();
        entry.setBookingId(bookingId);
        entry.setEventType(eventType);
        entry.setActorUserId(actorUserId);
        entry.setActorType(actorType);
        entry.setPayload(payloadJson);
        repository.save(entry);
    }
}
