package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.OrderStatus;
import com.restaurant.exception.BadRequestException;
import com.restaurant.exception.InvalidOrderStateException;
import com.restaurant.exception.OrderNotFoundException;
import com.restaurant.exception.StatusConflictException;
import com.restaurant.model.Order;
import com.restaurant.repository.OrderRepository;
import com.restaurant.util.ApiMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * // OOP: Layers, Enums (state machine), Custom Exceptions
 * Kitchen operations: live ticket queue + guarded status transitions.
 */
@Service
public class KitchenService {

    private final OrderRepository orderRepo;

    public KitchenService(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    /** Active tickets (PLACED/ACCEPTED/PREPARING/READY) from the DB view, optionally filtered. */
    public List<Dtos.KitchenOrderTicket> getActiveTickets(String statusFilter) {
        OrderStatus filter = statusFilter == null || statusFilter.isBlank()
                ? null
                : parseStatus(statusFilter);

        return orderRepo.findActiveForKitchen().stream()
                .filter(t -> filter == null || t.getStatus() == filter)
                .map(ApiMapper::toKitchenTicket)
                .toList();
    }

    /**
     * Moves a ticket along the state machine with optimistic concurrency.
     *
     * // OOP: Enums – OrderStatus.canTransitionTo() centralises the legal transitions.
     *
     *  1. reject an illegal transition (400) using the *expected* source status
     *  2. run UPDATE orders SET status=? WHERE order_id=? AND status=? (Rule: prepared)
     *  3. 0 rows changed ⇒ somebody clicked first ⇒ 409 with expected/actual statuses
     */
    public Dtos.OrderResponse updateStatus(Long orderId, Dtos.UpdateOrderStatusRequest request,
                                          Long changedByStaffId) {
        OrderStatus from = parseStatus(request.from());
        OrderStatus to = parseStatus(request.to());

        orderRepo.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));

        // 1. Validate the transition against the state machine.
        if (!from.canTransitionTo(to)) {
            throw new InvalidOrderStateException(orderId, from, to);
        }

        // 2 + 3. Guarded update detects a concurrent click.
        int rows = orderRepo.updateStatus(orderId, from, to, changedByStaffId);
        if (rows == 0) {
            OrderStatus actual = orderRepo.findById(orderId)
                    .map(Order::getStatus)
                    .orElseThrow(() -> new OrderNotFoundException(orderId));
            throw new StatusConflictException(orderId, from, actual);
        }

        Order updated = orderRepo.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        String tableNumber = orderRepo.findTableNumberByOrderId(orderId).orElse(null);
        return ApiMapper.toOrderResponse(updated, tableNumber);
    }

    private static OrderStatus parseStatus(String raw) {
        if (raw == null) throw new BadRequestException("Status value is required");
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown order status: " + raw);
        }
    }
}
