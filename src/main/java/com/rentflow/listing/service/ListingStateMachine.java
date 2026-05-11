package com.rentflow.listing.service;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.listing.entity.ListingStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ListingStateMachine {

    private static final Map<ListingStatus, Set<ListingStatus>> ALLOWED_TRANSITIONS = Map.of(
        ListingStatus.DRAFT,           Set.of(ListingStatus.PENDING_APPROVAL, ListingStatus.ARCHIVED),
        ListingStatus.PENDING_APPROVAL, Set.of(ListingStatus.ACTIVE, ListingStatus.DRAFT, ListingStatus.ARCHIVED),
        ListingStatus.ACTIVE,           Set.of(ListingStatus.SUSPENDED, ListingStatus.ARCHIVED),
        ListingStatus.SUSPENDED,       Set.of(ListingStatus.ACTIVE, ListingStatus.ARCHIVED),
        ListingStatus.ARCHIVED,         Set.of()
    );

    public void validateTransition(ListingStatus from, ListingStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessRuleException("INVALID_STATUS_TRANSITION",
                "Cannot transition listing from " + from + " to " + to);
        }
    }

    public boolean canTransition(ListingStatus from, ListingStatus to) {
        if (from == null || to == null) {
            return false;
        }
        Set<ListingStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}
