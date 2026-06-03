package com.rentflow.listing.service;

import java.util.UUID;

public interface VehicleListingPort {

    int archiveListings(UUID vehicleId);

    int suspendListings(UUID vehicleId, String reason, String source);
}
