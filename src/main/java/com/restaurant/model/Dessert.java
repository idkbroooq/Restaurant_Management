package com.restaurant.model;

import com.restaurant.enums.ItemType;

import java.math.BigDecimal;

/**
 * // OOP: Inheritance – extends abstract MenuItem
 * Represents a dessert item. No spice level or volume constraints.
 * Mid-range preparation time.
 */
public class Dessert extends MenuItem {

    private static final int BASE_PREP_MINUTES = 10;

    public Dessert(Long id, Long categoryId, String name, String description,
                   BigDecimal price, boolean available) {
        super(id, categoryId, name, description, price, ItemType.DESSERT,
                available, null, null); // neither spiceLevel nor volumeMl applies
    }

    /**
     * // OOP: Polymorphism – desserts take moderate time to plate and serve.
     */
    @Override
    public int getPreparationTimeMinutes() {
        return BASE_PREP_MINUTES;
    }

    // getFinalPrice() uses default (base price) → no override needed
}
