package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * // OOP: Layers, Encapsulation
 * Admin category management (MANAGER only).
 */
@RestController
@RequestMapping("/api/admin/categories")
@Tag(name = "Admin - Category Management", description = "Menu classification and display ordering")
public class AdminCategoryController {

    private final MenuService menuService;

    public AdminCategoryController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping
    @Operation(summary = "List all categories")
    public ResponseEntity<List<Dtos.CategoryDto>> getCategories() {
        return ResponseEntity.ok(menuService.getCategories());
    }

    @PostMapping
    @Operation(summary = "Create menu category")
    public ResponseEntity<Dtos.CategoryDto> createCategory(@Valid @RequestBody Dtos.CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuService.createCategory(request));
    }
}
