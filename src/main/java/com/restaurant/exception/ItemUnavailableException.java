package com.restaurant.exception;

/**
 * // OOP: Custom Exceptions, Inheritance
 * Thrown when an order references an item that is inactive, discontinued, or 86'd.
 */
public class ItemUnavailableException extends RestaurantException {

    private final Long itemId;

    /** Convenience constructor with plain message (itemId unknown at call site). */
    public ItemUnavailableException(String message) {
        super("BAD_REQUEST", message);
        this.itemId = null;
    }

    public ItemUnavailableException(Long itemId, String itemName) {
        super("BAD_REQUEST", String.format("Menu item '%s' (ID: %d) is currently unavailable or sold out.", itemName, itemId));
        this.itemId = itemId;
    }

    public Long getItemId() {
        return itemId;
    }
}
