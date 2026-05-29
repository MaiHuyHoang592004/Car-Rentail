package com.rentflow.listing.controller;

import com.rentflow.common.exception.ValidationException;
import com.rentflow.listing.service.AdminListingService;
import com.rentflow.listing.service.ListingService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ListingControllerValidationTest {

    @Test
    void hostListListingsWithInvalidStatusThrowsValidationException() {
        ListingController controller = new ListingController(mock(ListingService.class));

        assertThatThrownBy(() -> controller.listListings(
                "INVALID",
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt"),
                null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid listing status: INVALID");
    }

    @Test
    void adminListListingsWithInvalidStatusThrowsValidationException() {
        AdminListingController controller = new AdminListingController(mock(AdminListingService.class));

        assertThatThrownBy(() -> controller.listListings(
                "INVALID",
                null,
                null,
                0,
                20,
                Sort.by(Sort.Direction.DESC, "createdAt")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Invalid listing status: INVALID");
    }
}
