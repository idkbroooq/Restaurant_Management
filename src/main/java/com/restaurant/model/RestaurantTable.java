package com.restaurant.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * // OOP: Encapsulation – all fields private/final, validated in constructor
 * Represents a physical dining table in the restaurant.
 * Each table has a unique QR token used by customers to open the menu.
 */
public class RestaurantTable {

    private final Long id;
    private final String tableNumber;   // e.g. "T01", "T02"
    private final String qrToken;       // UUID-based, unique per table
    private final Integer capacity;     // number of seats
    private final boolean active;
    private final LocalDateTime createdAt;

    // OOP: Encapsulation – only constructor sets fields; no setters on immutable fields
    public RestaurantTable(Long id, String tableNumber, String qrToken,
                           Integer capacity, boolean active, LocalDateTime createdAt) {
        if (tableNumber == null || tableNumber.isBlank()) {
            throw new IllegalArgumentException("tableNumber must not be blank");
        }
        if (qrToken == null || qrToken.isBlank()) {
            throw new IllegalArgumentException("qrToken must not be blank");
        }
        if (capacity == null || capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.id = id;
        this.tableNumber = tableNumber.trim();
        this.qrToken = qrToken.trim();
        this.capacity = capacity;
        this.active = active;
        this.createdAt = createdAt;
    }

    public Long getId()              { return id; }
    public String getTableNumber()   { return tableNumber; }
    public String getQrToken()       { return qrToken; }
    public Integer getCapacity()     { return capacity; }
    public boolean isActive()        { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RestaurantTable that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "RestaurantTable{id=" + id + ", tableNumber='" + tableNumber
                + "', qrToken='" + qrToken + "', capacity=" + capacity + "}";
    }
}
