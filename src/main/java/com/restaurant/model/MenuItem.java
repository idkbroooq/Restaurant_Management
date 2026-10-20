package com.restaurant.model;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * // OOP: Abstract class, Inheritance, Polymorphism, Encapsulation
 * Abstract base for all menu items. Concrete subclasses are Food, Beverage, Dessert.
 *
 * Demonstrates:
 *  - abstract getPreparationTimeMinutes() → must be overridden (Abstraction)
 *  - overridable getFinalPrice() → default is base price, subclasses may vary (Polymorphism)
 *  - private final fields, constructor validation (Encapsulation)
 */
public abstract class MenuItem {

    // // OOP: Encapsulation – private, read-only after construction
    private final Long id;
    private final Long categoryId;
    private final String name;
    private final String description;
    private final BigDecimal price;      // base price; DB is DECIMAL(10,2)
    private final ItemType itemType;
    private final boolean available;

    // nullable: only Food uses spiceLevel; only Beverage uses volumeMl
    private final SpiceLevel spiceLevel; // nullable for non-food items
    private final Integer volumeMl;      // nullable for non-beverage items

    protected MenuItem(Long id, Long categoryId, String name, String description,
                       BigDecimal price, ItemType itemType, boolean available,
                       SpiceLevel spiceLevel, Integer volumeMl) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MenuItem name must not be blank");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("MenuItem price must be positive");
        }
        this.id = id;
        this.categoryId = categoryId;
        this.name = name.trim();
        this.description = description;
        this.price = price;
        this.itemType = itemType;
        this.available = available;
        this.spiceLevel = spiceLevel;
        this.volumeMl = volumeMl;
    }

    // --- Abstract methods (Abstraction) ---

    /**
     * // OOP: Abstraction – subclass must declare its kitchen preparation window.
     */
    public abstract int getPreparationTimeMinutes();

    // --- Overridable template method (Polymorphism) ---

    /**
     * // OOP: Polymorphism – default returns base price; subclasses may override.
     * Returns the final price offered to customers.
     *
     * @return effective selling price (never null or ≤ 0)
     */
    public BigDecimal getFinalPrice() {
        return price;
    }

    // --- Getters (Encapsulation) ---

    public Long getId()            { return id; }
    public Long getCategoryId()    { return categoryId; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice()   { return price; }
    public ItemType getItemType()  { return itemType; }
    public boolean isAvailable()   { return available; }
    public SpiceLevel getSpiceLevel() { return spiceLevel; }
    public Integer getVolumeMl()   { return volumeMl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MenuItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", name='" + name
                + "', price=" + price + ", type=" + itemType + "}";
    }
}
