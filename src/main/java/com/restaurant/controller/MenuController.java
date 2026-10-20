package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Customer-facing menu catalogue: active categories with their available items.
 */
@RestController
@RequestMapping("/api/menu")
@Tag(name = "Customer - Menu", description = "Customer-facing menu catalog")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @Operation(summary = "Get active customer menu")
    public ResponseEntity<List<Dtos.MenuCategoryWithItems>> getCustomerMenu(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String itemType) {
        return ResponseEntity.ok(menuService.getGroupedMenu(categoryId, itemType));
    }
}
