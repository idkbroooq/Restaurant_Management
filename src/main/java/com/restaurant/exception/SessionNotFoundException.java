package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when a dining session is not found or is not in the expected state.
 */
public class SessionNotFoundException extends RestaurantException {

    public SessionNotFoundException(Long sessionId) {
        super("NOT_FOUND", "Dining session #" + sessionId + " was not found.");
    }
}
