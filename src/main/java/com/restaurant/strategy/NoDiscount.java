package com.restaurant.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * // OOP: Strategy Pattern, Polymorphism
 * Default discount strategy: applies zero reduction to the bill.
 */
public class NoDiscount implements DiscountStrategy {

    @Override
    public BigDecimal applyDiscount(BigDecimal subtotal) {
        Objects.requireNonNull(subtotal, "Subtotal cannot be null");
        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getDescription() {
        return "No Discount";
    }

    @Override
    public String toString() {
        return "NoDiscount{}";
    }
}
