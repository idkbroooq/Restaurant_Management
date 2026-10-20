package com.restaurant.exception;

/**
 * // OOP: Custom Exception Hierarchy, Abstraction
 * Abstract root checked/unchecked runtime exception for all restaurant domain errors.
 */
public abstract class RestaurantException extends RuntimeException {

    private final String errorCode;

    public RestaurantException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RestaurantException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
