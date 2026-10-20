package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when a request conflicts with the current state of a resource
 * (e.g. duplicate transaction reference, unsettled orders during payment).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RestaurantException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }
}
