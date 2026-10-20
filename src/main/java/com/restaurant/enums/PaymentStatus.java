package com.restaurant.enums;

/**
 * // OOP: Type-Safe Enums
 * State of an invoice settlement transaction.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,  // payment confirmed and settled
    FAILED,
    REFUNDED
}
