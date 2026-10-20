package com.restaurant.exception;

import com.restaurant.enums.OrderStatus;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when an illegal lifecycle transition is attempted on an order.
 */
public class InvalidOrderStateException extends RestaurantException {

    private final OrderStatus currentStatus;
    private final OrderStatus attemptedStatus;

    /** Convenience constructor for plain-message usage (e.g. from Order domain methods). */
    public InvalidOrderStateException(String message) {
        super("BAD_REQUEST", message);
        this.currentStatus = null;
        this.attemptedStatus = null;
    }

    public InvalidOrderStateException(Long orderId, OrderStatus currentStatus, OrderStatus attemptedStatus) {
        super("BAD_REQUEST", String.format(
            "Cannot transition order #%d from current status '%s' to '%s'. Allowed transitions: %s",
            orderId, currentStatus, attemptedStatus, currentStatus.getNextStatuses()
        ));
        this.currentStatus = currentStatus;
        this.attemptedStatus = attemptedStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }

    public OrderStatus getAttemptedStatus() {
        return attemptedStatus;
    }
}
