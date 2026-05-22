package com.rentflow.listing.service;

import com.rentflow.common.exception.ListingNotFoundException;
import com.rentflow.common.web.PageResponse;
import com.rentflow.listing.dto.*;
import com.rentflow.listing.entity.Listing;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.repository.ListingRepository;
import com.rentflow.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ListingSearchService {

    private final ListingRepository listingRepository;
    private final VehicleRepository vehicleRepository;

    public PageResponse<ListingSearchResponse> search(ListingSearchRequest request) {
        validateDateRange(request);

        ListingSearchCriteria criteria = new ListingSearchCriteria(
            request.city(),
            request.categories() != null && request.categories().isEmpty() ? null : request.categories(),
            request.minPrice(),
            request.maxPrice(),
            request.seats(),
            request.transmission(),
            request.fuelType(),
            request.pickupDate(),
            request.returnDate()
        );

        Pageable pageable = PageRequest.of(request.page(), request.size());
        Page<ListingSearchResponse> page = listingRepository.search(criteria, pageable);

        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }

    public ListingDetailResponse getListingDetail(UUID listingId) {
        Listing listing = listingRepository
            .findByIdAndStatusWithExtras(listingId, ListingStatus.ACTIVE)
            .orElseThrow(() -> new ListingNotFoundException(listingId.toString()));

        return ListingDetailResponse.from(
            listing,
            vehicleRepository.findById(listing.getVehicleId()).orElse(null),
            listing.getExtras()
        );
    }

    private void validateDateRange(ListingSearchRequest request) {
        boolean hasPickup = request.pickupDate() != null;
        boolean hasReturn = request.returnDate() != null;
        if (hasPickup != hasReturn) {
            throw new IllegalArgumentException(
                "Both pickupDate and returnDate must be provided together, or neither");
        }
        if (hasPickup && !request.pickupDate().isBefore(request.returnDate())) {
            throw new IllegalArgumentException("pickupDate must be strictly before returnDate");
        }
    }
}
