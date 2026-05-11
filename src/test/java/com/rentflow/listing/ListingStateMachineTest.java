package com.rentflow.listing;

import com.rentflow.common.exception.BusinessRuleException;
import com.rentflow.listing.entity.ListingStatus;
import com.rentflow.listing.service.ListingStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ListingStateMachineTest {

    private ListingStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new ListingStateMachine();
    }

    @Test
    void draft_canTransitionToPendingApprovalOrArchived() {
        assertThat(stateMachine.canTransition(ListingStatus.DRAFT, ListingStatus.PENDING_APPROVAL)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.DRAFT, ListingStatus.ARCHIVED)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.DRAFT, ListingStatus.ACTIVE)).isFalse();
    }

    @Test
    void pendingApproval_canTransitionToActiveDraftOrArchived() {
        assertThat(stateMachine.canTransition(ListingStatus.PENDING_APPROVAL, ListingStatus.ACTIVE)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.PENDING_APPROVAL, ListingStatus.DRAFT)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.PENDING_APPROVAL, ListingStatus.ARCHIVED)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.PENDING_APPROVAL, ListingStatus.SUSPENDED)).isFalse();
    }

    @Test
    void active_canTransitionToSuspendedOrArchived() {
        assertThat(stateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.SUSPENDED)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.ARCHIVED)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.DRAFT)).isFalse();
        assertThat(stateMachine.canTransition(ListingStatus.ACTIVE, ListingStatus.PENDING_APPROVAL)).isFalse();
    }

    @Test
    void suspended_canTransitionToActiveOrArchived() {
        assertThat(stateMachine.canTransition(ListingStatus.SUSPENDED, ListingStatus.ACTIVE)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.SUSPENDED, ListingStatus.ARCHIVED)).isTrue();
        assertThat(stateMachine.canTransition(ListingStatus.SUSPENDED, ListingStatus.DRAFT)).isFalse();
    }

    @Test
    void archived_cannotTransitionToAnything() {
        for (ListingStatus from : ListingStatus.values()) {
            assertThat(stateMachine.canTransition(ListingStatus.ARCHIVED, from)).isFalse();
        }
    }

    @Test
    void validateTransition_throwsOnInvalidTransition() {
        assertThatThrownBy(() -> stateMachine.validateTransition(ListingStatus.ACTIVE, ListingStatus.DRAFT))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Cannot transition listing from ACTIVE to DRAFT");
    }

    @Test
    void validateTransition_doesNotThrowOnValidTransition() {
        assertThatNoException()
            .isThrownBy(() -> stateMachine.validateTransition(ListingStatus.DRAFT, ListingStatus.PENDING_APPROVAL));
    }

    @Test
    void nullFromStatus_returnsFalse() {
        assertThat(stateMachine.canTransition(null, ListingStatus.ACTIVE)).isFalse();
    }

    @Test
    void nullToStatus_returnsFalse() {
        assertThat(stateMachine.canTransition(ListingStatus.ACTIVE, null)).isFalse();
    }
}
