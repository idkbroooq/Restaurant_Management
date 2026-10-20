package com.restaurant.model;

import com.restaurant.enums.ItemType;

import java.math.BigDecimal;

/**
 * // OOP: Inheritance – extends abstract MenuItem
 * Represents a beverage. Requires volume_ml (CHECK in DB).
 * No spice level; faster to prepare than food.
 */
public class Beverage extends MenuItem {

    private static final int BASE_PREP_MINUTES = 5;

    public Beverage(Long id, Long categoryId, String name, String description,
                    BigDecimal price, boolean available, Integer volumeMl) {
        super(id, categoryId, name, description, price, ItemType.BEVERAGE,
                available, null, volumeMl); // spiceLevel not applicable
    }

    /**
     * // OOP: Polymorphism – beverages are quick to prepare.
     */
    @Override
    public int getPreparationTimeMinutes() {
        return BASE_PREP_MINUTES;
    }

    // getFinalPrice() uses default (base price) → no override needed
}
