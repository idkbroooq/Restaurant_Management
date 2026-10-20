package com.restaurant.repository;

import com.restaurant.enums.OrderStatus;
import com.restaurant.model.BillTotals;
import com.restaurant.model.KitchenTicket;
import com.restaurant.model.Order;
import com.restaurant.model.OrderSummary;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 */
public interface OrderRepository extends Repository<Order, Long> {

    @Override
    Optional<Order> findById(Long id);

    /** Every order ever placed (used by reports/admin auditing). */
    @Override
    List<Order> findAll();

    List<Order> findBySessionId(Long sessionId);

    /**
     * Kitchen queue built from the {@code v_kitchen_active_orders} view
     * (PLACED/ACCEPTED/PREPARING/READY), already carrying elapsed minutes.
     */
    List<KitchenTicket> findActiveForKitchen();

    /** Compact order rows (with item counts) used on the bill/tracking screens. */
    List<OrderSummary> findSummariesBySessionId(Long sessionId);

    /** Invokes the {@code sp_generate_bill} stored procedure (subtotal + 5% GST). */
    Optional<BillTotals> generateBill(Long sessionId);

    /** Inserts order header + all order_item rows inside ONE transaction (Rule 4). */
    Order save(Order order);

    /**
     * Optimistic-concurrency status update:
     * {@code UPDATE orders SET status=? WHERE order_id=? AND status=?}.
     *
     * @return number of rows changed — 0 means somebody else already moved the ticket
     */
    int updateStatus(Long orderId, OrderStatus from, OrderStatus to, Long changedByStaffId);

    /** Resolves the display table number for an order (via its dining session). */
    Optional<String> findTableNumberByOrderId(Long orderId);
}
