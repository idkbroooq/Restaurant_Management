package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when a table QR token or table numeric ID cannot be resolved.
 */
public class TableNotFoundException extends RestaurantException {

    public TableNotFoundException(String identifier) {
        super("NOT_FOUND", String.format("Restaurant table '%s' was not found or is currently inactive.", identifier));
    }
}
