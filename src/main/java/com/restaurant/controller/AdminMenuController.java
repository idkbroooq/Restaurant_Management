package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Admin menu management (MANAGER only). Deletes are always soft deletes (Rule 10).
 */
@RestController
@RequestMapping("/api/admin/menu")
@Tag(name = "Admin - Menu Management", description = "Dish catalog management and availability toggling")
public class AdminMenuController {

    private final MenuService menuService;

    public AdminMenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @Operation(summary = "Admin menu catalog", description = "All non-deleted items including sold-out ones.")
    public ResponseEntity<List<Dtos.MenuItemDto>> getAdminMenu() {
        return ResponseEntity.ok(menuService.getAllItemsForAdmin());
    }

    @PostMapping
    @Operation(summary = "Create new menu item")
    public ResponseEntity<Dtos.MenuItemDto> createMenuItem(@Valid @RequestBody Dtos.CreateMenuItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createMenuItem(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update menu item details")
    public ResponseEntity<Dtos.MenuItemDto> updateMenuItem(@PathVariable Long id,
                                                           @Valid @RequestBody Dtos.CreateMenuItemRequest request) {
        return ResponseEntity.ok(menuService.updateMenuItem(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete menu item", description = "Keeps historical order lines intact.")
    public ResponseEntity<Dtos.MessageResponse> deleteMenuItem(@PathVariable Long id) {
        return ResponseEntity.ok(menuService.softDeleteItem(id));
    }

    @PatchMapping("/{id}/availability")
    @Operation(summary = "Quick-toggle 86 / availability")
    public ResponseEntity<Dtos.MenuItemDto> toggleAvailability(@PathVariable Long id,
                                                               @Valid @RequestBody Dtos.AvailabilityRequest request) {
        return ResponseEntity.ok(menuService.setAvailability(id, request.isAvailable()));
    }
}
