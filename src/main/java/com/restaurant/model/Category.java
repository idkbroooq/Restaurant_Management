package com.restaurant.model;

import java.util.Objects;

/**
 * // OOP: Encapsulation – private final fields, constructor validation
 * Represents a menu category (e.g. Starters, Mains, Beverages).
 */
public class Category {

    private final Long id;
    private final String name;
    private final String description;
    private final Integer displayOrder;
    private final boolean active;

    public Category(Long id, String name, String description, Integer displayOrder, boolean active) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        this.id = id;
        this.name = name.trim();
        this.description = description;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.active = active;
    }

    public Long getId()            { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Integer getDisplayOrder() { return displayOrder; }
    public boolean isActive()      { return active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Category that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Category{id=" + id + ", name='" + name + "'}";
    }
}
