package com.rentflow.file.repository;

import com.rentflow.file.entity.ListingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, UUID> {

    long countByListingId(UUID listingId);

    List<ListingPhoto> findByListingIdOrderByDisplayOrderAsc(UUID listingId);

    Optional<ListingPhoto> findByIdAndListingId(UUID id, UUID listingId);

    List<ListingPhoto> findByListingIdInOrderByDisplayOrderAsc(Collection<UUID> listingIds);
}
