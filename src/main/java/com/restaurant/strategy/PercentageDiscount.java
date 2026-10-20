package com.restaurant.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * // OOP: Strategy Pattern, Polymorphism, Encapsulation
 * Applies a percentage markdown (e.g. 10%, 15%) to an order subtotal.
 */
public class PercentageDiscount implements DiscountStrategy {

    private final BigDecimal percentage;

    public PercentageDiscount(BigDecimal percentage) {
        Objects.requireNonNull(percentage, "Percentage cannot be null");
        if (percentage.compareTo(BigDecimal.ZERO) < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Discount percentage must be between 0.00 and 100.00");
        }
        this.percentage = percentage.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    @Override
    public BigDecimal applyDiscount(BigDecimal subtotal) {
        Objects.requireNonNull(subtotal, "Subtotal cannot be null");
        if (subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal discountFraction = percentage.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal discountAmount = subtotal.multiply(discountFraction).setScale(2, RoundingMode.HALF_UP);
        return subtotal.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getDescription() {
        return percentage.toPlainString() + "% Promotional Discount";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PercentageDiscount that)) return false;
        return Objects.equals(percentage, that.percentage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(percentage);
    }

    @Override
    public String toString() {
        return "PercentageDiscount{" + "percentage=" + percentage + '}';
    }
}
