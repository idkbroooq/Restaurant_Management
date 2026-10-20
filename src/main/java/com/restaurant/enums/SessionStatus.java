package com.restaurant.enums;

/**
 * // OOP: Type-Safe Enums
 * Lifecycle status of a dining session (customers at a table).
 * Matches DB ENUM: ACTIVE | COMPLETED | CANCELLED
 */
public enum SessionStatus {
    ACTIVE,
    COMPLETED,
    CANCELLED
}
