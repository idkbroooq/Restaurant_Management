package com.restaurant.model;

import com.restaurant.enums.OrderStatus;
import com.restaurant.exception.EmptyOrderException;
import com.restaurant.exception.InvalidOrderStateException;
import com.restaurant.exception.ItemUnavailableException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * // OOP: Composition – Order owns a List<OrderItem> (not inheritance)
 * // OOP: Encapsulation – items list is only modified through addItem/removeItem
 * // OOP: Generics/Collections – uses List<OrderItem> + streams for calculation
 * // OOP: Comparator – static comparator for sorting orders by creation time
 *
 * Represents a kitchen order ticket placed from a dining session.
 * DB columns: order_id, session_id, status, total_amount, special_notes, created_at, updated_at
 */
public class Order {

    // OOP: Comparator (Generics) – compare orders oldest-first for kitchen queue
    public static final Comparator<Order> BY_CREATED_AT =
            Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final Long id;
    private final Long sessionId;
    private OrderStatus status;            // mutable; transitions enforced
    private final String specialNotes;     // optional kitchen note per order
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // OOP: Composition – Order is composed of its line items
    private final List<OrderItem> items;   // controlled via addItem/removeItem

    public Order(Long id, Long sessionId, OrderStatus status,
                 String specialNotes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.status = status != null ? status : OrderStatus.PLACED;
        this.specialNotes = specialNotes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.items = new ArrayList<>(); // OOP: Collections
    }

    /**
     * // OOP: Encapsulation – external code cannot directly mutate the items list.
     * Adds a validated order item to this order.
     *
     * @throws ItemUnavailableException if item is not marked available
     * @throws InvalidOrderStateException if order is not in PLACED state
     */
    public void addItem(OrderItem item, boolean menuItemAvailable) {
        if (status != OrderStatus.PLACED) {
            throw new InvalidOrderStateException(
                    "Items can only be added to a PLACED order; current status: " + status);
        }
        if (!menuItemAvailable) {
            throw new ItemUnavailableException("Menu item " + item.getMenuItemId() + " is not available");
        }
        items.add(item);
        updatedAt = LocalDateTime.now();
    }

    /**
     * Removes an item from the order (only when PLACED).
     */
    public void removeItem(OrderItem item) {
        if (status != OrderStatus.PLACED) {
            throw new InvalidOrderStateException(
                    "Items can only be removed from a PLACED order; current status: " + status);
        }
        items.remove(item);
        updatedAt = LocalDateTime.now();
    }

    /**
     * // OOP: Streams (Generics/Collections) – calculates subtotal without loops.
     * Sums unit_price * quantity over all items.
     *
     * @throws EmptyOrderException if there are no items
     */
    public BigDecimal calculateSubtotal() {
        if (items.isEmpty()) {
            throw new EmptyOrderException("Cannot calculate subtotal of an order with no items");
        }
        // OOP: Streams – uses reduce over collections
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * // OOP: Streams – tolerant subtotal used by read-only DTO mapping.
     * Returns ZERO for an item-less order instead of throwing, so the mapper can
     * render the response without special-casing empty orders.
     */
    public BigDecimal calculateSubtotalOrZero() {
        return items.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Transitions order to a new status; validates transition via OrderStatus.canTransitionTo().
     *
     * @throws InvalidOrderStateException if transition is illegal
     */
    public void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new InvalidOrderStateException(
                    "Illegal order status transition: " + status + " → " + next);
        }
        this.status = next;
        this.updatedAt = LocalDateTime.now();
    }

    /** Returns an unmodifiable view of items (Encapsulation). */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    /** Used when loading items from DB after constructing the Order. */
    public void setItemsFromDb(List<OrderItem> dbItems) {
        this.items.clear();
        this.items.addAll(dbItems);
    }

    public Long getId()               { return id; }
    public Long getSessionId()        { return sessionId; }
    public OrderStatus getStatus()    { return status; }
    public String getSpecialNotes()   { return specialNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Order{id=" + id + ", sessionId=" + sessionId
                + ", status=" + status + ", items=" + items.size() + "}";
    }
}
