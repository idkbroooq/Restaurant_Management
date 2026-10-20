package com.restaurant.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * // OOP: Strategy Pattern, Polymorphism, Encapsulation
 * Happy Hour discount: 20% discount on orders exceeding a minimum qualifying spend.
 */
public class HappyHourDiscount implements DiscountStrategy {

    private final BigDecimal discountRate;
    private final BigDecimal minimumSpendThreshold;

    public HappyHourDiscount() {
        this(new BigDecimal("20.00"), new BigDecimal("300.00"));
    }

    public HappyHourDiscount(BigDecimal discountRate, BigDecimal minimumSpendThreshold) {
        Objects.requireNonNull(discountRate, "Discount rate cannot be null");
        Objects.requireNonNull(minimumSpendThreshold, "Minimum spend threshold cannot be null");
        if (discountRate.compareTo(BigDecimal.ZERO) < 0 || discountRate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Happy hour discount rate must be between 0% and 100%");
        }
        this.discountRate = discountRate.setScale(2, RoundingMode.HALF_UP);
        this.minimumSpendThreshold = minimumSpendThreshold.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal applyDiscount(BigDecimal subtotal) {
        Objects.requireNonNull(subtotal, "Subtotal cannot be null");
        if (subtotal.compareTo(minimumSpendThreshold) < 0) {
            // Did not meet minimum spend threshold; no discount applied
            return subtotal.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rateFraction = discountRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal discount = subtotal.multiply(rateFraction).setScale(2, RoundingMode.HALF_UP);
        return subtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getDescription() {
        return "Happy Hour (" + discountRate.toPlainString() + "% off on orders over ₹" + minimumSpendThreshold.toPlainString() + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HappyHourDiscount that)) return false;
        return Objects.equals(discountRate, that.discountRate) &&
               Objects.equals(minimumSpendThreshold, that.minimumSpendThreshold);
    }

    @Override
    public int hashCode() {
        return Objects.hash(discountRate, minimumSpendThreshold);
    }

    @Override
    public String toString() {
        return "HappyHourDiscount{" +
                "discountRate=" + discountRate +
                ", minimumSpendThreshold=" + minimumSpendThreshold +
                '}';
    }
}
