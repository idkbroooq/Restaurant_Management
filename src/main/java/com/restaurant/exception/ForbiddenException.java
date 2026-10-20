package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when an authenticated staff member lacks the role required for an action.
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenException extends RestaurantException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
