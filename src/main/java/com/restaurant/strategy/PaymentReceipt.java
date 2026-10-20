package com.restaurant.strategy;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * // OOP: Encapsulation, Value Object
 * Immutable receipt generated upon payment processing.
 */
public class PaymentReceipt {

    private final String receiptNumber;
    private final BigDecimal amountPaid;
    private final PaymentMethodType methodType;
    private final PaymentStatus status;
    private final String transactionRef;
    private final Instant processedAt;

    public PaymentReceipt(String receiptNumber, BigDecimal amountPaid, PaymentMethodType methodType,
                          PaymentStatus status, String transactionRef, Instant processedAt) {
        this.receiptNumber = Objects.requireNonNull(receiptNumber, "Receipt number cannot be null");
        this.amountPaid = Objects.requireNonNull(amountPaid, "Amount paid cannot be null");
        this.methodType = Objects.requireNonNull(methodType, "Payment method cannot be null");
        this.status = Objects.requireNonNull(status, "Payment status cannot be null");
        this.transactionRef = transactionRef;
        this.processedAt = Objects.requireNonNull(processedAt, "ProcessedAt timestamp cannot be null");
    }

    public String getReceiptNumber() { return receiptNumber; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public PaymentMethodType getMethodType() { return methodType; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionRef() { return transactionRef; }
    public Instant getProcessedAt() { return processedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentReceipt that)) return false;
        return Objects.equals(receiptNumber, that.receiptNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiptNumber);
    }

    @Override
    public String toString() {
        return "PaymentReceipt{" +
                "receiptNumber='" + receiptNumber + '\'' +
                ", amountPaid=" + amountPaid +
                ", methodType=" + methodType +
                ", status=" + status +
                ", transactionRef='" + transactionRef + '\'' +
                ", processedAt=" + processedAt +
                '}';
    }
}
