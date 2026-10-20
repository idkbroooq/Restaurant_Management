package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * // OOP: Layers, Encapsulation
 * Billing and payment settlement. Protected by the WAITER/MANAGER interceptor.
 */
@RestController
@RequestMapping("/api/sessions")
@Tag(name = "Customer - Billing & Payments", description = "Bill calculation and payment settlement")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/{id}/bill")
    @Operation(summary = "Generate bill breakdown for a session",
            description = "Uses the sp_generate_bill stored procedure (subtotal + 5% GST).")
    public ResponseEntity<Dtos.BillResponse> generateBill(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.generateBill(id));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Settle session bill payment",
            description = "Records the payment and closes the dining session (COMPLETED).")
    public ResponseEntity<Dtos.PaymentResponse> pay(@PathVariable Long id,
                                                    @Valid @RequestBody Dtos.PaymentRequest request) {
        return ResponseEntity.ok(billingService.processPayment(id, request));
    }
}
