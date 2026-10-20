package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import com.restaurant.enums.SessionStatus;
import com.restaurant.exception.BadRequestException;
import com.restaurant.exception.ConflictException;
import com.restaurant.exception.SessionNotFoundException;
import com.restaurant.model.BillTotals;
import com.restaurant.model.DiningSession;
import com.restaurant.model.Payment;
import com.restaurant.repository.OrderRepository;
import com.restaurant.repository.PaymentRepository;
import com.restaurant.repository.SessionRepository;
import com.restaurant.strategy.DiscountStrategy;
import com.restaurant.strategy.PaymentMethod;
import com.restaurant.strategy.PaymentReceipt;
import com.restaurant.factory.PaymentFactory;
import com.restaurant.util.ApiMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * // OOP: Layers, Strategy Pattern (DiscountStrategy + PaymentMethod), Factory Pattern (PaymentFactory)
 * Billing + payment settlement for a dining session.
 */
@Service
public class BillingService {

    private final SessionRepository sessionRepo;
    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final PaymentFactory paymentFactory;      // OOP: Factory Pattern
    private final DiscountStrategy discountStrategy;  // OOP: Strategy Pattern (injected bean)

    public BillingService(SessionRepository sessionRepo, OrderRepository orderRepo,
                          PaymentRepository paymentRepo, PaymentFactory paymentFactory,
                          DiscountStrategy discountStrategy) {
        this.sessionRepo = sessionRepo;
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.paymentFactory = paymentFactory;
        this.discountStrategy = discountStrategy;
    }

    /**
     * Builds the invoice for a session using the {@code sp_generate_bill} procedure
     * (subtotal + 5% GST) and the per-order summary rows.
     */
    public Dtos.BillResponse generateBill(Long sessionId) {
        DiningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getStatus() == SessionStatus.CANCELLED) {
            throw new BadRequestException("Session #" + sessionId + " is cancelled; no bill can be generated.");
        }

        BillTotals totals = orderRepo.generateBill(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (totals.activeOrderCount() == 0) {
            throw new BadRequestException("Session #" + sessionId + " has no eligible orders to bill.");
        }

        List<Dtos.OrderSummaryResponse> orders = orderRepo.findSummariesBySessionId(sessionId).stream()
                .map(ApiMapper::toOrderSummary)
                .toList();

        return new Dtos.BillResponse(
                totals.sessionId(), totals.tableNumber(), totals.sessionStatus(),
                totals.activeOrderCount(), totals.subtotal(), totals.taxPercentage(),
                totals.taxAmount(), totals.grandTotal(), orders);
    }

    /**
     * Settles a session.
     *
     * // OOP: Transaction – payment row + session close commit together.
     * // OOP: Strategy – the discount algorithm and the payment channel are chosen at runtime.
     */
    @Transactional
    public Dtos.PaymentResponse processPayment(Long sessionId, Dtos.PaymentRequest request) {
        DiningSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));

        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BadRequestException(
                    "Session #" + sessionId + " is already " + session.getStatus() + "; nothing to settle.");
        }

        BillTotals totals = orderRepo.generateBill(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (totals.activeOrderCount() == 0) {
            throw new BadRequestException("Session #" + sessionId + " has no eligible orders to settle.");
        }

        BigDecimal grandTotal = totals.grandTotal();
        // OOP: Strategy – default bean is NoDiscount, so payable == grandTotal.
        BigDecimal payable = discountStrategy.applyDiscount(grandTotal);

        if (request.amount() == null || request.amount().compareTo(grandTotal) != 0) {
            throw new BadRequestException("Payment amount " + request.amount()
                    + " does not match the outstanding grand total " + grandTotal + ".");
        }

        PaymentMethodType methodType = parseMethod(request.paymentMethod());
        // OOP: Factory + Polymorphism – caller only sees the PaymentMethod interface.
        PaymentMethod method = paymentFactory.create(methodType);
        PaymentReceipt receipt = method.pay(payable, request.transactionRef());

        BigDecimal taxAmount = totals.taxAmount();
        BigDecimal discountAmount = grandTotal.subtract(payable);

        Payment payment = new Payment(null, sessionId, totals.subtotal(), taxAmount, discountAmount,
                payable, methodType, PaymentStatus.PENDING, receipt.getTransactionRef(),
                LocalDateTime.now(), null);

        Payment saved;
        try {
            saved = paymentRepo.save(payment);
            // Confirms the row and stamps paid_at = NOW().
            paymentRepo.updateStatus(saved.getId(), PaymentStatus.COMPLETED, receipt.getTransactionRef());
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(transaction_ref) violated → a duplicate reference was submitted.
            throw new ConflictException("Duplicate transaction reference: " + request.transactionRef());
        }

        // Close the dining session (COMPLETED + end_time).
        sessionRepo.updateStatus(sessionId, SessionStatus.COMPLETED);

        Payment confirmed = paymentRepo.findById(saved.getId()).orElse(saved);
        return new Dtos.PaymentResponse(
                confirmed.getId(), sessionId, confirmed.getTotalAmount(),
                confirmed.getMethod().name(), confirmed.getStatus().name(),
                confirmed.getTransactionRef(), confirmed.getPaidAt(),
                SessionStatus.COMPLETED.name());
    }

    private static PaymentMethodType parseMethod(String raw) {
        if (raw == null) throw new BadRequestException("paymentMethod is required");
        try {
            return PaymentMethodType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported payment method: " + raw);
        }
    }
}
