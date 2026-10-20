package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.KitchenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Kitchen operations: the live ticket queue and guarded state transitions.
 * Both endpoints are protected by the CHEF/MANAGER interceptor (see WebConfig).
 */
@RestController
@Tag(name = "Kitchen Operations", description = "Real-time kitchen ticket queue and order state transitions")
public class KitchenController {

    private final KitchenService kitchenService;

    public KitchenController(KitchenService kitchenService) {
        this.kitchenService = kitchenService;
    }

    @GetMapping("/api/kitchen/orders")
    @Operation(summary = "Kitchen real-time ticket queue")
    public ResponseEntity<List<Dtos.KitchenOrderTicket>> getActiveOrders(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(kitchenService.getActiveTickets(status));
    }

    @PatchMapping("/api/orders/{id}/status")
    @Operation(summary = "Update order status along the lifecycle",
            description = "Optimistic concurrency: 409 if the current status no longer equals 'from'.")
    public ResponseEntity<Dtos.OrderResponse> updateStatus(@PathVariable Long id,
                                                           @Valid @RequestBody Dtos.UpdateOrderStatusRequest request,
                                                           HttpSession session) {
        // The audit trigger records which staff member made the change.
        Long staffId = AuthController.currentStaff(session).staffId();
        return ResponseEntity.ok(kitchenService.updateStatus(id, request, staffId));
    }
}
