package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.TableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * // OOP: Layers, Encapsulation
 * Customer-facing table resolution by QR token (token validated by TableTokenInterceptor).
 */
@RestController
@RequestMapping("/api/tables")
@Tag(name = "Customer - Tables & Sessions", description = "QR code token resolution and table lookup")
public class TableController {

    private final TableService tableService;

    public TableController(TableService tableService) {
        this.tableService = tableService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Resolve table by QR token")
    public ResponseEntity<Dtos.TableSessionResponse> getTableByToken(@PathVariable String token) {
        return ResponseEntity.ok(tableService.getTableByToken(token));
    }
}
