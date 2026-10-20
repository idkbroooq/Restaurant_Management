package com.restaurant.strategy;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * // OOP: Strategy Pattern, Polymorphism
 * Settles bill via physical currency at cashier register.
 */
public class CashPayment implements PaymentMethod {

    @Override
    public PaymentReceipt pay(BigDecimal amount, String reference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
        String ref = (reference != null && !reference.isBlank()) ? reference : "CASH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String receiptId = "RCP-CSH-" + System.currentTimeMillis();
        return new PaymentReceipt(receiptId, amount, PaymentMethodType.CASH, PaymentStatus.COMPLETED, ref, Instant.now());
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.CASH;
    }
}
