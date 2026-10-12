package com.civicconnect.service;

import org.junit.jupiter.api.Test;

import static com.civicconnect.model.ServiceRequest.Status.*;
import static org.assertj.core.api.Assertions.assertThat;

/** DEC-011 / FR-009 / FR-011: the allowed status moves. Plain unit test - no database or Spring needed. */
class TransitionPolicyTest {

    private final TransitionPolicy policy = new TransitionPolicy();

    @Test
    void followsTheHappyPath() {
        assertThat(policy.canMove(NEW, ASSIGNED)).isTrue();
        assertThat(policy.canMove(ASSIGNED, IN_PROGRESS)).isTrue();
        assertThat(policy.canMove(IN_PROGRESS, RESOLVED)).isTrue();
        assertThat(policy.canMove(RESOLVED, CLOSED)).isTrue();
    }

    @Test
    void rejectedIsOnlyPossibleFromNewOrAssigned() {
        assertThat(policy.canMove(NEW, REJECTED)).isTrue();
        assertThat(policy.canMove(ASSIGNED, REJECTED)).isTrue();
        assertThat(policy.canMove(IN_PROGRESS, REJECTED)).isFalse();
        assertThat(policy.canMove(RESOLVED, REJECTED)).isFalse();
    }

    @Test
    void cannotSkipSteps() {
        assertThat(policy.canMove(NEW, RESOLVED)).isFalse();
        assertThat(policy.canMove(NEW, CLOSED)).isFalse();
        assertThat(policy.canMove(ASSIGNED, CLOSED)).isFalse();
    }

    @Test
    void closedAndRejectedAreReadOnly() {
        assertThat(policy.isTerminal(CLOSED)).isTrue();
        assertThat(policy.isTerminal(REJECTED)).isTrue();
        assertThat(policy.allowedFrom(CLOSED)).isEmpty();
        assertThat(policy.allowedFrom(REJECTED)).isEmpty();
        assertThat(policy.canMove(CLOSED, IN_PROGRESS)).isFalse();
    }

    @Test
    void allowedFromReturnsACopy() {
        policy.allowedFrom(NEW).clear();
        assertThat(policy.allowedFrom(NEW)).containsExactlyInAnyOrder(ASSIGNED, REJECTED);
    }
}
