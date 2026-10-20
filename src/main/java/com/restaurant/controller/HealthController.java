package com.restaurant.controller;

import com.restaurant.dto.HealthResponseDto;
import com.restaurant.service.HealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * // OOP: Encapsulation, Separation of Concerns
 * REST controller providing health probe endpoints.
 * Layer 5: controller -> service -> repository -> DB.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "System", description = "Operational health and readiness checks")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check probe", description = "Executes SELECT 1 against MySQL database to verify connectivity.")
    public ResponseEntity<HealthResponseDto> getHealth() {
        HealthResponseDto health = healthService.checkHealth();
        if ("CONNECTED".equals(health.getDatabase())) {
            return ResponseEntity.ok(health);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }
}
