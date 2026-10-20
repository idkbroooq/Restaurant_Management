package com.restaurant.model;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * // OOP: Encapsulation + Interfaces (Payable, see below)
 * Records a completed or pending payment for a dining session.
 * Money stored as BigDecimal (Rule 3).
 */
public class Payment {

    private final Long id;
    private final Long sessionId;
    private final BigDecimal subtotal;      // sum of order items before tax
    private final BigDecimal taxAmount;     // calculated tax (e.g. 5% GST)
    private final BigDecimal discountAmount;
    private final BigDecimal totalAmount;   // subtotal + tax - discount
    private final PaymentMethodType method;
    private PaymentStatus status;
    private final String transactionRef;   // UPI ref / card last4 / "CASH"
    private final LocalDateTime createdAt;
    private LocalDateTime paidAt;          // set when confirmed

    public Payment(Long id, Long sessionId, BigDecimal subtotal, BigDecimal taxAmount,
                   BigDecimal discountAmount, BigDecimal totalAmount,
                   PaymentMethodType method, PaymentStatus status,
                   String transactionRef, LocalDateTime createdAt, LocalDateTime paidAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.subtotal = subtotal;
        this.taxAmount = taxAmount;
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.method = method;
        this.status = status;
        this.transactionRef = transactionRef;
        this.createdAt = createdAt;
        this.paidAt = paidAt;
    }

    /** Marks payment as confirmed. */
    public void confirm(String ref) {
        this.status = PaymentStatus.COMPLETED;
        this.paidAt = LocalDateTime.now();
    }

    public Long getId()                   { return id; }
    public Long getSessionId()            { return sessionId; }
    public BigDecimal getSubtotal()       { return subtotal; }
    public BigDecimal getTaxAmount()      { return taxAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotalAmount()    { return totalAmount; }
    public PaymentMethodType getMethod()  { return method; }
    public PaymentStatus getStatus()      { return status; }
    public String getTransactionRef()     { return transactionRef; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
    public LocalDateTime getPaidAt()      { return paidAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Payment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Payment{id=" + id + ", sessionId=" + sessionId
                + ", total=" + totalAmount + ", status=" + status + "}";
    }
}
