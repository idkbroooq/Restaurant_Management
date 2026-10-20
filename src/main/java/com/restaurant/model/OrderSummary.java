package com.restaurant.model;

import com.restaurant.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * // OOP: Encapsulation, Value Object
 * Compact projection of an order used on tracking/bill screens: header fields
 * plus the number of lines, computed in SQL (no N+1 item loading).
 */
public record OrderSummary(Long orderId, Long sessionId, OrderStatus status,
                           BigDecimal totalAmount, int itemCount, LocalDateTime createdAt) {}
