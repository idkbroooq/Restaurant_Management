package com.restaurant.strategy;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * // OOP: Strategy Pattern, Polymorphism
 * Settles bill via credit/debit card swipe machine terminal.
 */
public class CardPayment implements PaymentMethod {

    @Override
    public PaymentReceipt pay(BigDecimal amount, String reference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
        String ref = (reference != null && !reference.isBlank()) ? reference : "CRD-AUTH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String receiptId = "RCP-CRD-" + System.currentTimeMillis();
        return new PaymentReceipt(receiptId, amount, PaymentMethodType.CARD, PaymentStatus.COMPLETED, ref, Instant.now());
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.CARD;
    }
}
