package com.restaurant.strategy;

import com.restaurant.enums.PaymentMethodType;

import java.math.BigDecimal;

/**
 * // OOP: Strategy Pattern, Interface Abstraction, Polymorphism
 * Contract for different payment processing mechanisms.
 */
public interface PaymentMethod {

    /**
     * Executes the payment and generates an immutable receipt.
     *
     * @param amount positive money amount to collect
     * @param reference optional customer transaction reference or cash drawer tag
     * @return PaymentReceipt containing confirmed settlement status
     */
    PaymentReceipt pay(BigDecimal amount, String reference);

    /**
     * Returns the payment channel identifier.
     */
    PaymentMethodType getMethodType();
}
