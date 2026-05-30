package com.rentflow.listing.service;

import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class VehicleListingAdapter implements VehicleListingPort {

    private final ListingRepository listingRepository;

    @Override
    public int archiveListings(UUID vehicleId) {
        return listingRepository.updateStatusByVehicleIdAndStatusNot(
                vehicleId, ListingStatus.ARCHIVED, ListingStatus.ARCHIVED);
    }

    @Override
    public int suspendListings(UUID vehicleId) {
        return listingRepository.updateStatusByVehicleIdAndStatus(
                vehicleId, ListingStatus.ACTIVE, ListingStatus.SUSPENDED);
    }
}
