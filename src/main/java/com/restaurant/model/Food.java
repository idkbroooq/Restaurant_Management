package com.restaurant.model;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;

import java.math.BigDecimal;

/**
 * // OOP: Inheritance – extends abstract MenuItem; Polymorphism – overrides getPreparationTimeMinutes()
 * Represents a food dish. Requires spice_level (CHECK in DB).
 */
public class Food extends MenuItem {

    // Standard kitchen prep time for food items (minutes)
    private static final int BASE_PREP_MINUTES = 15;

    public Food(Long id, Long categoryId, String name, String description,
                BigDecimal price, boolean available, SpiceLevel spiceLevel) {
        super(id, categoryId, name, description, price, ItemType.FOOD,
                available, spiceLevel, null); // volumeMl not applicable
    }

    /**
     * // OOP: Polymorphism – concrete implementation of abstract method.
     * Food items take longer to prepare than beverages.
     */
    @Override
    public int getPreparationTimeMinutes() {
        return BASE_PREP_MINUTES;
    }
}
