package com.restaurant.model;

import java.math.BigDecimal;

/**
 * // OOP: Encapsulation, Value Object
 * Immutable totals returned by the {@code sp_generate_bill} stored procedure:
 * the bill for one dining session (subtotal, 5% GST, grand total) plus the
 * session/table identity used to render the invoice.
 */
public record BillTotals(Long sessionId, String tableNumber, String sessionStatus,
                         int activeOrderCount, BigDecimal subtotal,
                         BigDecimal taxPercentage, BigDecimal taxAmount,
                         BigDecimal grandTotal) {}
