package com.restaurant.strategy;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * // OOP: Strategy Pattern, Polymorphism
 * Settles bill via unified payments interface (UPI / QR scanner).
 */
public class UpiPayment implements PaymentMethod {

    @Override
    public PaymentReceipt pay(BigDecimal amount, String reference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
        String ref = (reference != null && !reference.isBlank()) ? reference : "UPI-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String receiptId = "RCP-UPI-" + System.currentTimeMillis();
        return new PaymentReceipt(receiptId, amount, PaymentMethodType.UPI, PaymentStatus.COMPLETED, ref, Instant.now());
    }

    @Override
    public PaymentMethodType getMethodType() {
        return PaymentMethodType.UPI;
    }
}
