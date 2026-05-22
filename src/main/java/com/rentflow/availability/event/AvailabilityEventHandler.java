package com.rentflow.availability.event;

import com.rentflow.availability.service.AvailabilityService;
import com.rentflow.listing.event.ListingApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AvailabilityEventHandler {

    private final AvailabilityService availabilityService;

    /**
     * Generate the 365-day availability calendar when a listing is approved.
     * Runs synchronously in the publisher's transaction so a generation
     * failure rolls back the approval (matches the prior direct-call semantic).
     */
    @EventListener
    public void onListingApproved(ListingApprovedEvent event) {
        availabilityService.generateForListing(event.listingId());
    }
}
