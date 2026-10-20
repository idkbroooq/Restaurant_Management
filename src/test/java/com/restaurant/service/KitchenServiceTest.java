package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.OrderStatus;
import com.restaurant.exception.InvalidOrderStateException;
import com.restaurant.exception.StatusConflictException;
import com.restaurant.model.Order;
import com.restaurant.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KitchenService} state-machine enforcement.
 * Prompt 7: "returning a conflict if the status changed meanwhile".
 */
@ExtendWith(MockitoExtension.class)
class KitchenServiceTest {

    @Mock private OrderRepository orderRepo;

    private KitchenService service;

    private static final Long ORDER_ID = 7L;

    @BeforeEach
    void setUp() {
        service = new KitchenService(orderRepo);
    }

    private Order orderWith(OrderStatus status) {
        return new Order(ORDER_ID, 10L, status, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("updateStatus: an illegal transition is rejected before any UPDATE runs")
    void updateStatus_invalidTransition() {
        when(orderRepo.findById(ORDER_ID)).thenReturn(Optional.of(orderWith(OrderStatus.PLACED)));

        // PLACED -> SERVED skips ACCEPTED/PREPARING/READY, so it is illegal.
        Dtos.UpdateOrderStatusRequest req =
                new Dtos.UpdateOrderStatusRequest("PLACED", "SERVED", null);

        assertThrows(InvalidOrderStateException.class,
                () -> service.updateStatus(ORDER_ID, req, 1L));
        verify(orderRepo, never()).updateStatus(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("updateStatus: a legal transition issues the guarded UPDATE")
    void updateStatus_success() {
        when(orderRepo.findById(ORDER_ID))
                .thenReturn(Optional.of(orderWith(OrderStatus.PLACED)))
                .thenReturn(Optional.of(orderWith(OrderStatus.ACCEPTED)));
        when(orderRepo.updateStatus(ORDER_ID, OrderStatus.PLACED, OrderStatus.ACCEPTED, 1L)).thenReturn(1);
        when(orderRepo.findTableNumberByOrderId(ORDER_ID)).thenReturn(Optional.of("T01"));

        Dtos.UpdateOrderStatusRequest req =
                new Dtos.UpdateOrderStatusRequest("PLACED", "ACCEPTED", null);
        Dtos.OrderResponse res = service.updateStatus(ORDER_ID, req, 1L);

        assertEquals("ACCEPTED", res.status());
        assertEquals("T01", res.tableNumber());
        verify(orderRepo).updateStatus(ORDER_ID, OrderStatus.PLACED, OrderStatus.ACCEPTED, 1L);
    }

    @Test
    @DisplayName("updateStatus: 0 rows changed means a concurrent click -> 409 conflict")
    void updateStatus_conflictWhenStatusChangedMeanwhile() {
        when(orderRepo.findById(ORDER_ID))
                .thenReturn(Optional.of(orderWith(OrderStatus.PLACED)))   // read for validation
                .thenReturn(Optional.of(orderWith(OrderStatus.ACCEPTED))); // actual status now
        // Someone already moved the ticket, so the guarded UPDATE matches no rows.
        when(orderRepo.updateStatus(ORDER_ID, OrderStatus.PLACED, OrderStatus.ACCEPTED, 1L)).thenReturn(0);

        Dtos.UpdateOrderStatusRequest req =
                new Dtos.UpdateOrderStatusRequest("PLACED", "ACCEPTED", null);

        StatusConflictException ex = assertThrows(StatusConflictException.class,
                () -> service.updateStatus(ORDER_ID, req, 1L));
        assertEquals(OrderStatus.PLACED, ex.getExpected());
        assertEquals(OrderStatus.ACCEPTED, ex.getActual());
    }
}
