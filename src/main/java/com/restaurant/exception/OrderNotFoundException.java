package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when a requested order does not exist.
 */
public class OrderNotFoundException extends RestaurantException {

    public OrderNotFoundException(Long orderId) {
        super("NOT_FOUND", "Order #" + orderId + " was not found.");
    }
}
