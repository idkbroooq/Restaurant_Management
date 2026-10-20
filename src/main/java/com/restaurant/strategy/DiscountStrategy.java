package com.restaurant.strategy;

import java.math.BigDecimal;

/**
 * // OOP: Strategy Pattern, Interface Abstraction, Polymorphism
 * Common algorithm strategy for computing bill discounts.
 */
public interface DiscountStrategy {

    /**
     * Calculates the discounted amount from raw order subtotal.
     *
     * @param subtotal raw non-negative order subtotal
     * @return net bill amount after discount applied (never less than zero)
     */
    BigDecimal applyDiscount(BigDecimal subtotal);

    /**
     * Human-readable description of the discount applied.
     */
    String getDescription();
}
