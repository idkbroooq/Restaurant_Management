package com.restaurant.repository;

import com.restaurant.model.MenuCategoryGroup;
import com.restaurant.model.MenuItem;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 * Data Access Object contract for menu_item.
 */
public interface MenuItemRepository extends Repository<MenuItem, Long> {

    /** Every non-deleted item (admin view — includes sold-out items). */
    @Override
    List<MenuItem> findAll();

    /** Only items flagged available and not soft-deleted (customer view). */
    List<MenuItem> findAllAvailable();

    /** Active categories with their available items, in ONE grouped query. */
    List<MenuCategoryGroup> findMenuGroupedByCategory();

    List<MenuItem> findByCategory(Long categoryId);

    @Override
    Optional<MenuItem> findById(Long id);

    MenuItem save(MenuItem item);

    /** Full update used by the admin menu editor. */
    void update(MenuItem item);

    void updateAvailability(Long id, boolean available);

    /** Soft delete: never hard-deletes rows that may have order history (Rule 10). */
    void softDelete(Long id);
}
