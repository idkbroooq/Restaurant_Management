package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.OrderStatus;
import com.restaurant.enums.SessionStatus;
import com.restaurant.enums.SpiceLevel;
import com.restaurant.exception.EmptyOrderException;
import com.restaurant.exception.ItemUnavailableException;
import com.restaurant.model.DiningSession;
import com.restaurant.model.Food;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.model.RestaurantTable;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.repository.OrderRepository;
import com.restaurant.repository.SessionRepository;
import com.restaurant.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}.
 * Covers the five scenarios required by Prompt 7:
 * success, unavailable item, empty cart, invalid quantity and rollback on failure.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepo;
    @Mock private TableRepository tableRepo;
    @Mock private SessionRepository sessionRepo;
    @Mock private MenuItemRepository menuItemRepo;

    private OrderService service;

    private static final String TOKEN = "tok_tbl_abc";

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepo, tableRepo, sessionRepo, menuItemRepo);
    }

    private RestaurantTable activeTable() {
        return new RestaurantTable(1L, "T01", TOKEN, 4, true, LocalDateTime.now());
    }

    private MenuItem curry(boolean available) {
        return new Food(5L, 1L, "Paneer Curry", "creamy", new BigDecimal("120.00"),
                available, SpiceLevel.MEDIUM);
    }

    private DiningSession openSession() {
        return new DiningSession(10L, 1L, "sess_existing", SessionStatus.ACTIVE,
                LocalDateTime.now(), null);
    }

    // ------------------------------------------------------------------
    // 1. SUCCESS
    // ------------------------------------------------------------------
    @Test
    @DisplayName("placeOrder: happy path snapshots the DB price and opens a session")
    void placeOrder_success() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));
        when(sessionRepo.findOpenSessionByTableId(1L)).thenReturn(Optional.empty());
        when(sessionRepo.save(any(DiningSession.class))).thenAnswer(inv -> {
            DiningSession s = inv.getArgument(0);
            return new DiningSession(10L, s.getTableId(), s.getSessionToken(),
                    s.getStatus(), s.getStartTime(), null);
        });
        when(menuItemRepo.findById(5L)).thenReturn(Optional.of(curry(true)));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            Order saved = new Order(99L, o.getSessionId(), o.getStatus(),
                    o.getSpecialNotes(), o.getCreatedAt(), o.getUpdatedAt());
            saved.setItemsFromDb(o.getItems());
            return saved;
        });

        Dtos.CreateOrderRequest req = new Dtos.CreateOrderRequest(TOKEN, null, "no onion",
                List.of(new Dtos.OrderItemRequest(5L, 2, "less spicy")));

        Dtos.OrderResponse res = service.placeOrder(req);

        assertEquals(99L, res.orderId());
        assertEquals("T01", res.tableNumber());
        assertEquals("PLACED", res.status());
        assertEquals(1, res.items().size());
        // Rule 2 + Rule 3: the unit price comes from the DB (BigDecimal), not the client.
        assertEquals(new BigDecimal("120.00"), res.items().get(0).unitPrice());
        assertEquals(new BigDecimal("240.00"), res.totalAmount()); // 120.00 * 2
        verify(sessionRepo).save(any(DiningSession.class));
        verify(orderRepo).save(any(Order.class));
    }

    @Test
    @DisplayName("placeOrder: reuses the existing open dining session instead of opening a new one")
    void placeOrder_reusesOpenSession() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));
        when(sessionRepo.findOpenSessionByTableId(1L)).thenReturn(Optional.of(openSession()));
        when(menuItemRepo.findById(5L)).thenReturn(Optional.of(curry(true)));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            Order saved = new Order(99L, o.getSessionId(), o.getStatus(),
                    o.getSpecialNotes(), o.getCreatedAt(), o.getUpdatedAt());
            saved.setItemsFromDb(o.getItems());
            return saved;
        });

        Dtos.OrderResponse res = service.placeOrder(new Dtos.CreateOrderRequest(TOKEN, null, null,
                List.of(new Dtos.OrderItemRequest(5L, 1, null))));

        assertEquals(10L, res.sessionId());
        verify(sessionRepo, never()).save(any(DiningSession.class));
    }

    // ------------------------------------------------------------------
    // 2. UNAVAILABLE ITEM
    // ------------------------------------------------------------------
    @Test
    @DisplayName("placeOrder: rejects a sold-out item and saves nothing")
    void placeOrder_unavailableItem() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));
        when(sessionRepo.findOpenSessionByTableId(1L)).thenReturn(Optional.of(openSession()));
        when(menuItemRepo.findById(5L)).thenReturn(Optional.of(curry(false)));

        Dtos.CreateOrderRequest req = new Dtos.CreateOrderRequest(TOKEN, null, null,
                List.of(new Dtos.OrderItemRequest(5L, 1, null)));

        assertThrows(ItemUnavailableException.class, () -> service.placeOrder(req));
        verify(orderRepo, never()).save(any(Order.class));
    }

    // ------------------------------------------------------------------
    // 3. EMPTY CART
    // ------------------------------------------------------------------
    @Test
    @DisplayName("placeOrder: rejects an empty cart before touching the DB")
    void placeOrder_emptyCart() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));

        Dtos.CreateOrderRequest req = new Dtos.CreateOrderRequest(TOKEN, null, null, List.of());

        assertThrows(EmptyOrderException.class, () -> service.placeOrder(req));
        verify(orderRepo, never()).save(any(Order.class));
    }

    // ------------------------------------------------------------------
    // 4. INVALID QUANTITY
    // ------------------------------------------------------------------
    @Test
    @DisplayName("placeOrder: rejects a quantity outside 1..20")
    void placeOrder_invalidQuantity() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));

        Dtos.CreateOrderRequest req = new Dtos.CreateOrderRequest(TOKEN, null, null,
                List.of(new Dtos.OrderItemRequest(5L, 21, null)));

        assertThrows(com.restaurant.exception.BadRequestException.class, () -> service.placeOrder(req));
        verify(orderRepo, never()).save(any(Order.class));
    }

    // ------------------------------------------------------------------
    // 5. ROLLBACK ON FAILURE
    // ------------------------------------------------------------------
    @Test
    @DisplayName("placeOrder: a failure while persisting propagates (so @Transactional rolls back)")
    void placeOrder_rollsBackOnFailure() {
        when(tableRepo.findByToken(TOKEN)).thenReturn(Optional.of(activeTable()));
        when(sessionRepo.findOpenSessionByTableId(1L)).thenReturn(Optional.empty());
        when(sessionRepo.save(any(DiningSession.class))).thenReturn(openSession());
        when(menuItemRepo.findById(5L)).thenReturn(Optional.of(curry(true)));
        // Simulate the second INSERT (order_item) blowing up mid-transaction.
        when(orderRepo.save(any(Order.class)))
                .thenThrow(new DataAccessResourceFailureException("connection lost"));

        Dtos.CreateOrderRequest req = new Dtos.CreateOrderRequest(TOKEN, null, null,
                List.of(new Dtos.OrderItemRequest(5L, 1, null)));

        DataAccessResourceFailureException ex = assertThrows(DataAccessResourceFailureException.class,
                () -> service.placeOrder(req));
        assertTrue(ex.getMessage().contains("connection lost"));
        // The session insert happened first, but @Transactional guarantees the whole
        // unit of work (session + order + items) is rolled back when we throw.
        verify(sessionRepo, times(1)).save(any(DiningSession.class));
        verify(orderRepo, times(1)).save(any(Order.class));
        verify(orderRepo, never()).updateStatus(anyLong(), any(), any(), any());
    }
}
