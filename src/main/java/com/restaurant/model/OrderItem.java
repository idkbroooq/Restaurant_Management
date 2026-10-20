package com.restaurant.model;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * // OOP: Encapsulation – private final fields, validated constructor
 * A single line on an order ticket, capturing the price at order time (snapshot).
 * unit_price is read from the DB, never from the client (Rule 2).
 *
 * The item_type / spice_level / volume_ml fields are denormalised from menu_item
 * so a stored order can always be rendered (and priced) independent of later
 * menu edits.
 */
public class OrderItem {

    private final Long id;
    private final Long orderId;
    private final Long menuItemId;
    private final String menuItemName;          // denormalised snapshot
    private final ItemType itemType;            // denormalised snapshot
    private final SpiceLevel spiceLevel;        // nullable
    private final Integer volumeMl;             // nullable
    private final BigDecimal unitPrice;         // snapshot at order time (Rule 2 + 3)
    private final Integer quantity;
    private final String specialInstructions;   // optional customer note
    private final LocalDateTime createdAt;

    public OrderItem(Long id, Long orderId, Long menuItemId, String menuItemName,
                     ItemType itemType, SpiceLevel spiceLevel, Integer volumeMl,
                     BigDecimal unitPrice, Integer quantity, String specialInstructions,
                     LocalDateTime createdAt) {
        if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("unitPrice must be positive");
        }
        if (quantity == null || quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        this.id = id;
        this.orderId = orderId;
        this.menuItemId = menuItemId;
        this.menuItemName = menuItemName;
        this.itemType = itemType;
        this.spiceLevel = spiceLevel;
        this.volumeMl = volumeMl;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.specialInstructions = specialInstructions;
        this.createdAt = createdAt;
    }

    /** Computed line-total (never stored, always derived). */
    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public Long getId()                    { return id; }
    public Long getOrderId()               { return orderId; }
    public Long getMenuItemId()            { return menuItemId; }
    public String getMenuItemName()        { return menuItemName; }
    public ItemType getItemType()          { return itemType; }
    public SpiceLevel getSpiceLevel()       { return spiceLevel; }
    public Integer getVolumeMl()           { return volumeMl; }
    public BigDecimal getUnitPrice()       { return unitPrice; }
    public Integer getQuantity()           { return quantity; }
    public String getSpecialInstructions() { return specialInstructions; }
    public LocalDateTime getCreatedAt()    { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "OrderItem{id=" + id + ", menuItemId=" + menuItemId
                + ", qty=" + quantity + ", unitPrice=" + unitPrice + "}";
    }
}
