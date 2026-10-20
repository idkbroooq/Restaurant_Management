package com.restaurant.model;

import com.restaurant.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * // OOP: Composition
 * A denormalised kitchen ticket assembled from the {@code v_kitchen_active_orders}
 * view (one view row per dish) and grouped per order so the kitchen display can
 * render a card with all its lines. Carries the pre-computed {@code elapsedMinutes}
 * used for amber/red wait-time colouring.
 */
public class KitchenTicket {

    private final Long orderId;
    private final String tableNumber;
    private final OrderStatus status;
    private final LocalDateTime orderPlacedAt;
    private final int elapsedMinutes;
    private final String specialNotes;
    private final List<OrderItem> items = new ArrayList<>();

    public KitchenTicket(Long orderId, String tableNumber, OrderStatus status,
                         LocalDateTime orderPlacedAt, int elapsedMinutes, String specialNotes) {
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.status = status;
        this.orderPlacedAt = orderPlacedAt;
        this.elapsedMinutes = elapsedMinutes;
        this.specialNotes = specialNotes;
    }

    public void addItem(OrderItem item) { items.add(item); }

    public Long getOrderId()             { return orderId; }
    public String getTableNumber()       { return tableNumber; }
    public OrderStatus getStatus()       { return status; }
    public LocalDateTime getOrderPlacedAt() { return orderPlacedAt; }
    public int getElapsedMinutes()       { return elapsedMinutes; }
    public String getSpecialNotes()      { return specialNotes; }
    public List<OrderItem> getItems()    { return items; }
}
