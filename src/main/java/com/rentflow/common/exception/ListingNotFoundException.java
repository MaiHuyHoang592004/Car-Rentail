package com.rentflow.common.exception;

public class ListingNotFoundException extends ResourceNotFoundException {

    public ListingNotFoundException(String listingId) {
        super("LISTING_NOT_FOUND", "Listing", listingId);
    }
}
