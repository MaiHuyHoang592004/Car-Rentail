package com.rentflow.listing.repository;

import com.rentflow.listing.dto.ListingSearchCriteria;
import com.rentflow.listing.entity.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListingSearchRepositoryCustom {
    Page<Listing> search(ListingSearchCriteria criteria, Pageable pageable);
}
