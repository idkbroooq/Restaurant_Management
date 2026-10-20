package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when an order submission contains an empty item list.
 */
public class EmptyOrderException extends RestaurantException {

    public EmptyOrderException() {
        super("BAD_REQUEST", "Order submission must contain at least one item line.");
    }

    public EmptyOrderException(String message) {
        super("BAD_REQUEST", message);
    }
}
