package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;
import com.restaurant.exception.BadRequestException;
import com.restaurant.factory.MenuItemFactory;
import com.restaurant.model.Category;
import com.restaurant.model.MenuCategoryGroup;
import com.restaurant.model.MenuItem;
import com.restaurant.repository.CategoryRepository;
import com.restaurant.repository.MenuItemRepository;
import com.restaurant.util.ApiMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * // OOP: Layers – service delegates to repository (Rule 5); Polymorphism via MenuItemFactory
 * Menu catalogue + admin menu management. No SQL here (Rule 1); no client prices (Rule 2).
 */
@Service
public class MenuService {

    private final MenuItemRepository menuItemRepo;
    private final CategoryRepository categoryRepo;
    private final MenuItemFactory factory; // OOP: Factory Pattern

    public MenuService(MenuItemRepository menuItemRepo, CategoryRepository categoryRepo,
                       MenuItemFactory factory) {
        this.menuItemRepo = menuItemRepo;
        this.categoryRepo = categoryRepo;
        this.factory = factory;
    }

    // ===================== Customer menu =====================

    /** Active categories with their available items, from ONE grouped query (no N+1). */
    public List<Dtos.MenuCategoryWithItems> getGroupedMenu() {
        // OOP: Streams/Generics – map domain groups to contract DTOs
        return menuItemRepo.findMenuGroupedByCategory().stream()
                .map(this::toGroupDto)
                .toList();
    }

    /**
     * Customer menu with the optional {@code categoryId} / {@code itemType} filters
     * from GET /api/menu. Type filtering happens in-memory on the already-grouped result
     * (one DB query, no N+1).
     */
    public List<Dtos.MenuCategoryWithItems> getGroupedMenu(Long categoryId, String itemType) {
        ItemType typeFilter = parseItemTypeOrNull(itemType);
        return getGroupedMenu().stream()
                .filter(g -> categoryId == null || categoryId.equals(g.categoryId()))
                .map(g -> new Dtos.MenuCategoryWithItems(
                        g.categoryId(), g.name(), g.description(), g.displayOrder(),
                        g.items().stream()
                                .filter(i -> typeFilter == null || i.itemType().equals(typeFilter.name()))
                                .toList()))
                .filter(g -> typeFilter == null || !g.items().isEmpty())
                .toList();
    }

    public List<Dtos.MenuItemDto> getAvailableItems() {
        Map<Long, String> categoryNames = categoryNameLookup();
        return menuItemRepo.findAllAvailable().stream()
                .map(m -> ApiMapper.toMenuItemDto(m, categoryNames.get(m.getCategoryId())))
                .toList();
    }

    // ===================== Admin menu management =====================

    /** Admin view: every non-deleted item (including sold-out) with its category name. */
    public List<Dtos.MenuItemDto> getAllItemsForAdmin() {
        Map<Long, String> categoryNames = categoryNameLookup();
        return menuItemRepo.findAll().stream()
                .map(m -> ApiMapper.toMenuItemDto(m, categoryNames.get(m.getCategoryId())))
                .toList();
    }

    public Dtos.MenuItemDto createMenuItem(Dtos.CreateMenuItemRequest req) {
        Category category = categoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new BadRequestException("Category not found: " + req.categoryId()));

        ItemType type = parseItemType(req.itemType());
        SpiceLevel spice = parseSpiceLevel(req.spiceLevel());
        validateTypeAttributes(type, spice, req.volumeMl());

        MenuItem item = factory.create(null, category.getId(), req.name(), req.description(),
                req.price(), type, req.isAvailable() == null || req.isAvailable(), spice, req.volumeMl());

        return ApiMapper.toMenuItemDto(menuItemRepo.save(item), category.getName());
    }

    public Dtos.MenuItemDto updateMenuItem(Long id, Dtos.CreateMenuItemRequest req) {
        menuItemRepo.findById(id)
                .orElseThrow(() -> new BadRequestException("Menu item not found: " + id));
        Category category = categoryRepo.findById(req.categoryId())
                .orElseThrow(() -> new BadRequestException("Category not found: " + req.categoryId()));

        ItemType type = parseItemType(req.itemType());
        SpiceLevel spice = parseSpiceLevel(req.spiceLevel());
        validateTypeAttributes(type, spice, req.volumeMl());

        MenuItem updated = factory.create(id, category.getId(), req.name(), req.description(),
                req.price(), type, req.isAvailable() == null || req.isAvailable(), spice, req.volumeMl());
        menuItemRepo.update(updated);

        return ApiMapper.toMenuItemDto(
                menuItemRepo.findById(id).orElseThrow(() -> new BadRequestException("Menu item not found: " + id)),
                category.getName());
    }

    /** Quick 86 / availability toggle (used by kitchen + admin). */
    public Dtos.MenuItemDto setAvailability(Long id, boolean available) {
        menuItemRepo.findById(id)
                .orElseThrow(() -> new BadRequestException("Menu item not found: " + id));
        menuItemRepo.updateAvailability(id, available);
        MenuItem refreshed = menuItemRepo.findById(id).orElseThrow();
        return ApiMapper.toMenuItemDto(refreshed, categoryNameLookup().get(refreshed.getCategoryId()));
    }

    /** Soft delete only – history rows reference this item (Rule 10). */
    public Dtos.MessageResponse softDeleteItem(Long id) {
        MenuItem item = menuItemRepo.findById(id)
                .orElseThrow(() -> new BadRequestException("Menu item not found: " + id));
        menuItemRepo.softDelete(id);
        return new Dtos.MessageResponse(
                "Menu item '" + item.getName() + "' soft-deleted (is_deleted = TRUE, is_available = FALSE).");
    }

    // ===================== Categories =====================

    public List<Dtos.CategoryDto> getCategories() {
        return categoryRepo.findAll().stream()
                .map(ApiMapper::toCategoryDto)
                .toList();
    }

    public Dtos.CategoryDto createCategory(Dtos.CreateCategoryRequest req) {
        Category created = categoryRepo.save(new Category(
                null, req.name(), req.description(),
                req.displayOrder() != null ? req.displayOrder() : 0,
                req.isActive() == null || req.isActive()));
        return ApiMapper.toCategoryDto(created);
    }

    // ===================== Private helpers =====================

    private Dtos.MenuCategoryWithItems toGroupDto(MenuCategoryGroup group) {
        List<Dtos.MenuItemDto> items = group.items().stream()
                .map(m -> ApiMapper.toMenuItemDto(m, group.category().getName()))
                .toList();
        Category c = group.category();
        return new Dtos.MenuCategoryWithItems(
                c.getId(), c.getName(), c.getDescription(), c.getDisplayOrder(), items);
    }

    /** One query builds an id→name map so item DTOs avoid N+1 category lookups. */
    private Map<Long, String> categoryNameLookup() {
        return categoryRepo.findAll().stream()
                .collect(Collectors.toMap(Category::getId, Category::getName, (a, b) -> a));
    }

    private static ItemType parseItemType(String raw) {
        if (raw == null) throw new BadRequestException("itemType is required");
        try {
            return ItemType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown itemType: " + raw);
        }
    }

    /** Like {@link #parseItemType} but treats blank as "no filter" instead of an error. */
    private static ItemType parseItemTypeOrNull(String raw) {
        return (raw == null || raw.isBlank()) ? null : parseItemType(raw);
    }

    private static SpiceLevel parseSpiceLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return SpiceLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown spiceLevel: " + raw);
        }
    }

    /** Mirrors the DB CHECK chk_menu_item_type_attributes so callers get a 400, not a SQL error. */
    private static void validateTypeAttributes(ItemType type, SpiceLevel spice, Integer volumeMl) {
        switch (type) {
            case FOOD -> {
                if (volumeMl != null) throw new BadRequestException("FOOD items must not define volumeMl");
            }
            case BEVERAGE -> {
                if (volumeMl == null || volumeMl <= 0)
                    throw new BadRequestException("BEVERAGE items require a positive volumeMl");
                if (spice != null) throw new BadRequestException("BEVERAGE items must not define spiceLevel");
            }
            case DESSERT -> {
                if (volumeMl != null) throw new BadRequestException("DESSERT items must not define volumeMl");
                if (spice != null) throw new BadRequestException("DESSERT items must not define spiceLevel");
            }
        }
    }
}
