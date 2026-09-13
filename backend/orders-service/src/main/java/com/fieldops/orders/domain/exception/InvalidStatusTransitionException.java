package com.fieldops.orders.domain.exception;

import com.fieldops.orders.domain.model.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus current, OrderStatus target) {
        super("Cannot transition work order from status " + current + " to " + target);
    }
}