package com.restaurant.repository;

import com.restaurant.enums.PaymentStatus;
import com.restaurant.model.Payment;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 * Data access contract for the payment table. Money is BigDecimal (Rule 3).
 */
public interface PaymentRepository extends Repository<Payment, Long> {

    @Override
    Optional<Payment> findById(Long id);

    @Override
    List<Payment> findAll();

    Optional<Payment> findBySessionId(Long sessionId);

    Payment save(Payment payment);

    void updateStatus(Long paymentId, PaymentStatus status, String transactionRef);
}
