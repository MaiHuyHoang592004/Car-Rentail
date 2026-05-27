package com.rentflow.booking.service;

import java.util.UUID;

public interface BookingTimelineService {

    void append(UUID bookingId, String eventType, UUID actorUserId, String actorType, String payloadJson);
}
