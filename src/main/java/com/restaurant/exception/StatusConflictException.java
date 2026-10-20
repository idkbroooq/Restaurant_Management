package com.restaurant.exception;

import com.restaurant.enums.OrderStatus;

/**
 * // OOP: Custom Exceptions, Inheritance (extends ConflictException)
 * Thrown when an optimistic-concurrency update fails: the caller expected the
 * order to be in status {@code expected}, but the database currently holds a
 * different status (another staff member clicked first).
 * Maps to HTTP 409 Conflict and carries the expected/actual statuses for the UI.
 */
public class StatusConflictException extends ConflictException {

    private final OrderStatus expected;
    private final OrderStatus actual;

    public StatusConflictException(Long orderId, OrderStatus expected, OrderStatus actual) {
        super(String.format(
                "Status conflict: expected current status was '%s', but order #%d is currently '%s'.",
                expected, orderId, actual));
        this.expected = expected;
        this.actual = actual;
    }

    public OrderStatus getExpected() { return expected; }

    public OrderStatus getActual() { return actual; }
}
