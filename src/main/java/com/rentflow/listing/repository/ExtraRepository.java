package com.rentflow.listing.repository;

import com.rentflow.listing.entity.Extra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExtraRepository extends JpaRepository<Extra, UUID> {

    List<Extra> findByListingId(UUID listingId);

    List<Extra> findByListingIdAndActiveTrue(UUID listingId);

    void deleteByListingId(UUID listingId);
}
