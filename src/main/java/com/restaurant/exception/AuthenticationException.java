package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when authentication fails (wrong username/password) or token is missing.
 */
public class AuthenticationException extends RestaurantException {

    public AuthenticationException(String message) {
        super("UNAUTHORIZED", message);
    }
}
