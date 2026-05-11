package com.rentflow.common.exception;

public class ListingNotFoundException extends RentFlowException {

    public ListingNotFoundException(String listingId) {
        super("LISTING_NOT_FOUND", "Listing not found: " + listingId);
    }
}
