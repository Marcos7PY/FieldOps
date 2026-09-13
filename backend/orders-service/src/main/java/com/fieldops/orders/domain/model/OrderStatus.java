package com.fieldops.orders.domain.model;

public enum OrderStatus {
    DRAFT,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DRAFT -> target == ASSIGNED || target == CANCELLED;
            case ASSIGNED -> target == IN_PROGRESS || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}