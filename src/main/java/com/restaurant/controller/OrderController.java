package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Customer order placement and tracking.
 *
 * Rule 2: the request body carries NO prices — {@link OrderService} reads them from the DB.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Customer - Orders", description = "Order placement, live tracking, and table order history")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @Operation(summary = "Place a new order",
            description = "Atomic insert of order + items; prices come from the database, never the client.")
    public ResponseEntity<Dtos.OrderResponse> placeOrder(@Valid @RequestBody Dtos.CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    @GetMapping("/orders/{id}")
    @Operation(summary = "Get order details by ID")
    public ResponseEntity<Dtos.OrderResponse> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping("/tables/{token}/orders")
    @Operation(summary = "Get all orders for the table's active session")
    public ResponseEntity<List<Dtos.OrderSummaryResponse>> getOrdersByTableToken(@PathVariable String token) {
        return ResponseEntity.ok(orderService.getOrdersByTableToken(token));
    }
}
