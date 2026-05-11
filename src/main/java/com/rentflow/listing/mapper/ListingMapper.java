package com.rentflow.listing.mapper;

import com.rentflow.listing.dto.CreateListingRequest;
import com.rentflow.listing.dto.ExtraResponse;
import com.rentflow.listing.dto.ListingResponse;
import com.rentflow.listing.dto.ListingSummaryResponse;
import com.rentflow.listing.dto.UpdateListingRequest;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.vehicle.entity.Vehicle;
import com.rentflow.listing.entity.CancellationPolicy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class ListingMapper {

    public Listing toEntity(CreateListingRequest request, UUID hostId) {
        Listing listing = new Listing();
        listing.setVehicleId(request.vehicleId());
        listing.setHostId(hostId);
        listing.setTitle(request.title());
        listing.setDescription(request.description());
        listing.setCity(request.city());
        listing.setAddress(request.address());
        listing.setLatitude(request.latitude());
        listing.setLongitude(request.longitude());
        listing.setBasePricePerDay(request.basePricePerDay());
        listing.setCurrency(request.currency() != null ? request.currency() : "VND");
        listing.setDailyKmLimit(request.dailyKmLimit());
        listing.setInstantBook(request.instantBook() != null ? request.instantBook() : true);
        listing.setCancellationPolicy(
                request.cancellationPolicy() != null ? request.cancellationPolicy()
                        : CancellationPolicy.FLEXIBLE);
        listing.setStatus(ListingStatus.DRAFT);
        return listing;
    }

    public void applyUpdate(Listing listing, UpdateListingRequest request) {
        if (request.title() != null) listing.setTitle(request.title());
        if (request.description() != null) listing.setDescription(request.description());
        if (request.city() != null) listing.setCity(request.city());
        if (request.address() != null) listing.setAddress(request.address());
        if (request.latitude() != null) listing.setLatitude(request.latitude());
        if (request.longitude() != null) listing.setLongitude(request.longitude());
        if (request.basePricePerDay() != null) listing.setBasePricePerDay(request.basePricePerDay());
        if (request.dailyKmLimit() != null) listing.setDailyKmLimit(request.dailyKmLimit());
        if (request.instantBook() != null) listing.setInstantBook(request.instantBook());
        if (request.cancellationPolicy() != null) listing.setCancellationPolicy(request.cancellationPolicy());
    }

    public ListingSummaryResponse toSummaryResponse(Listing listing) {
        return ListingSummaryResponse.from(listing);
    }

    public ListingResponse toResponse(Listing listing, Vehicle vehicle, List<ExtraResponse> extras) {
        if (vehicle == null) {
            return ListingResponse.from(listing);
        }
        ListingResponse.VehicleSummary vehicleSummary = new ListingResponse.VehicleSummary(
                vehicle.getCategory(),
                vehicle.getMake(),
                vehicle.getModel(),
                vehicle.getManufactureYear(),
                vehicle.getTransmission(),
                vehicle.getFuelType(),
                vehicle.getSeats(),
                vehicle.getStatus()
        );
        return ListingResponse.from(listing, vehicleSummary, extras);
    }

    public Page<ListingSummaryResponse> toSummaryPage(Page<Listing> listings) {
        return listings.map(ListingSummaryResponse::from);
    }
}
