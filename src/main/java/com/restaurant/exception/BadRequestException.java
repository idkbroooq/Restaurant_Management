package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when a request is syntactically valid but violates a business rule
 * (e.g. quantity out of range, payment amount mismatch, session already settled).
 * Maps to HTTP 400 Bad Request.
 */
public class BadRequestException extends RestaurantException {

    public BadRequestException(String message) {
        super("BAD_REQUEST", message);
    }
}
