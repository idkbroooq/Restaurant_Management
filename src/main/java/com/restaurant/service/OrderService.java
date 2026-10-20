package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.OrderStatus;
import com.restaurant.enums.SessionStatus;
import com.restaurant.exception.BadRequestException;
import com.restaurant.exception.EmptyOrderException;
import com.restaurant.exception.ItemUnavailableException;
import com.restaurant.exception.OrderNotFoundException;
import com.restaurant.exception.SessionNotFoundException;
import com.restaurant.exception.TableNotFoundException;
import com.restaurant.model.DiningSession;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.model.OrderItem;
import com.restaurant.model.RestaurantTable;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.OrderRepository;
import com.restaurant.repository.SessionRepository;
import com.restaurant.repository.TableRepository;
import com.restaurant.util.ApiMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * // OOP: Layers, Encapsulation, Composition
 * Business logic for placing and reading customer orders.
 *
 * Rule 2: prices are read from the DB, never from the client.
 * Rule 4: placing an order is ONE database transaction (see {@link #placeOrder}).
 */
@Service
public class OrderService {

    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 20;

    private final OrderRepository orderRepo;
    private final TableRepository tableRepo;
    private final SessionRepository sessionRepo;
    private final MenuItemRepository menuItemRepo;

    public OrderService(OrderRepository orderRepo, TableRepository tableRepo,
                        SessionRepository sessionRepo, MenuItemRepository menuItemRepo) {
        this.orderRepo = orderRepo;
        this.tableRepo = tableRepo;
        this.sessionRepo = sessionRepo;
        this.menuItemRepo = menuItemRepo;
    }

    /**
     * Places a customer order.
     *
     * // OOP: Transaction (Rule 4) – every validation + both INSERTs happen atomically;
     * any thrown exception rolls the whole unit of work back, so no half-written ticket
     * can ever be persisted.
     *
     * Steps:
     *  1. resolve + validate the table token
     *  2. reject an empty cart
     *  3. for each line: reject unknown / unavailable items and out-of-range quantities
     *  4. read the authoritative price from the DB (never the client)
     *  5. reuse the open dining session, or open a new one
     *  6. insert orders + order_item with the unit_price snapshot
     */
    @Transactional
    public Dtos.OrderResponse placeOrder(Dtos.CreateOrderRequest request) {
        // 1. Table must exist and be active.
        RestaurantTable table = tableRepo.findByToken(request.tableToken())
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(request.tableToken()));

        // 2. Cart must not be empty.
        if (request.items() == null || request.items().isEmpty()) {
            throw new EmptyOrderException();
        }

        // 3. Reject duplicate lines (the DB has UNIQUE(order_id, item_id)).
        Set<Long> seenItemIds = new HashSet<>();
        for (Dtos.OrderItemRequest line : request.items()) {
            if (line.itemId() == null) {
                throw new BadRequestException("Each order line requires an itemId.");
            }
            if (!seenItemIds.add(line.itemId())) {
                throw new BadRequestException("Duplicate order line for item #" + line.itemId()
                        + "; merge quantities into a single line.");
            }
            if (line.quantity() == null || line.quantity() < MIN_QUANTITY || line.quantity() > MAX_QUANTITY) {
                throw new BadRequestException("Quantity for item #" + line.itemId()
                        + " must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY + ".");
            }
        }

        // 5. Bind to the open dining session, or open a new one for this table.
        DiningSession session = sessionRepo.findOpenSessionByTableId(table.getId())
                .orElseGet(() -> sessionRepo.save(new DiningSession(
                        null, table.getId(), "sess_" + UUID.randomUUID().toString().replace("-", ""),
                        SessionStatus.ACTIVE, LocalDateTime.now(), null)));

        Order order = new Order(null, session.getId(), OrderStatus.PLACED,
                request.specialNotes(), LocalDateTime.now(), LocalDateTime.now());

        // 3 + 4. Validate each item and snapshot its DB price.
        for (Dtos.OrderItemRequest line : request.items()) {
            MenuItem menuItem = menuItemRepo.findById(line.itemId())
                    .orElseThrow(() -> new BadRequestException("Menu item not found: " + line.itemId()));

            if (!menuItem.isAvailable()) {
                throw new ItemUnavailableException(menuItem.getId(), menuItem.getName());
            }

            // OOP: Polymorphism – getFinalPrice() resolves to the concrete subclass;
            // it returns the catalog price read from the database (Rule 2).
            OrderItem item = new OrderItem(
                    null, null, menuItem.getId(), menuItem.getName(),
                    menuItem.getItemType(), menuItem.getSpiceLevel(), menuItem.getVolumeMl(),
                    menuItem.getFinalPrice(),          // Rule 2 + Rule 3: DB price, BigDecimal
                    line.quantity(), line.itemNotes(), null);
            order.addItem(item, menuItem.isAvailable());
        }

        // 6. Header + lines inserted inside the same transaction.
        Order saved = orderRepo.save(order);
        return ApiMapper.toOrderResponse(saved, table.getTableNumber());
    }

    public Dtos.OrderResponse getOrderById(Long orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        String tableNumber = orderRepo.findTableNumberByOrderId(orderId).orElse(null);
        return ApiMapper.toOrderResponse(order, tableNumber);
    }

    /** Order history for the table's current active session. */
    public List<Dtos.OrderSummaryResponse> getOrdersByTableToken(String token) {
        RestaurantTable table = tableRepo.findByToken(token)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(token));

        DiningSession session = sessionRepo.findOpenSessionByTableId(table.getId())
                .orElseThrow(() -> new SessionNotFoundException(table.getId()));

        return orderRepo.findSummariesBySessionId(session.getId()).stream()
                .map(ApiMapper::toOrderSummary)
                .toList();
    }
}
