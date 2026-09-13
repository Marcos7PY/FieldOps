package com.fieldops.orders.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void draftCanTransitionToAssignedAndCancelled() {
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.ASSIGNED)).isTrue();
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.IN_PROGRESS)).isFalse();
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.DRAFT.canTransitionTo(OrderStatus.DRAFT)).isFalse();
        assertThat(OrderStatus.DRAFT.canTransitionTo(null)).isFalse();
    }

    @Test
    void assignedCanTransitionToInProgressAndCancelled() {
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.IN_PROGRESS)).isTrue();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.DRAFT)).isFalse();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.COMPLETED)).isFalse();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.ASSIGNED)).isFalse();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(null)).isFalse();
    }

    @Test
    void inProgressCanTransitionToCompletedAndCancelled() {
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.DRAFT)).isFalse();
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.ASSIGNED)).isFalse();
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(OrderStatus.IN_PROGRESS)).isFalse();
        assertThat(OrderStatus.IN_PROGRESS.canTransitionTo(null)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void completedIsTerminal(OrderStatus target) {
        assertThat(OrderStatus.COMPLETED.canTransitionTo(target)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void cancelledIsTerminal(OrderStatus target) {
        assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
    }

    @Test
    void terminalStatusesRejectNull() {
        assertThat(OrderStatus.COMPLETED.canTransitionTo(null)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(null)).isFalse();
    }
}