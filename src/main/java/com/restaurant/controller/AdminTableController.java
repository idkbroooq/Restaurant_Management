package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Admin table management + QR provisioning (MANAGER only).
 */
@RestController
@RequestMapping("/api/admin/tables")
@Tag(name = "Admin - Table Management", description = "Table topology, capacity, and QR token provisioning")
public class AdminTableController {

    private final TableService tableService;

    public AdminTableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping
    @Operation(summary = "List all restaurant tables")
    public ResponseEntity<List<Dtos.RestaurantTableDto>> getTables() {
        return ResponseEntity.ok(tableService.listTables());
    }

    @PostMapping
    @Operation(summary = "Create new restaurant table", description = "Auto-provisions a unique QR token.")
    public ResponseEntity<Dtos.RestaurantTableDto> createTable(@Valid @RequestBody Dtos.CreateTableRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTable(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update table attributes")
    public ResponseEntity<Dtos.RestaurantTableDto> updateTable(@PathVariable Long id,
                                                               @Valid @RequestBody Dtos.CreateTableRequest request) {
        return ResponseEntity.ok(tableService.updateTable(id, request));
    }

    @GetMapping("/{id}/qr")
    @Operation(summary = "Get table QR code configuration")
    public ResponseEntity<Dtos.TableQrResponse> getTableQr(@PathVariable Long id) {
        // Build the absolute base URL from the incoming request (host + port + context path).
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return ResponseEntity.ok(tableService.getQrCode(id, baseUrl));
    }
}
