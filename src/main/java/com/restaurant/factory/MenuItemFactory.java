package com.restaurant.factory;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;
import com.restaurant.model.Beverage;
import com.restaurant.model.Dessert;
import com.restaurant.model.Food;
import com.restaurant.model.MenuItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * // OOP: Factory Pattern – centralises creation of MenuItem subtype objects.
 * Reconstructs the correct subclass (Food/Beverage/Dessert) from a DB row.
 * Callers work only with the MenuItem interface; concrete type is hidden.
 */
@Component
public class MenuItemFactory {

    /**
     * Creates the appropriate MenuItem subclass based on itemType from the DB.
     *
     * @param id          PK from menu_item.item_id
     * @param categoryId  FK to category
     * @param name        item display name
     * @param description optional description
     * @param price       base price (DB DECIMAL(10,2))
     * @param itemType    FOOD / BEVERAGE / DESSERT
     * @param available   is_available flag
     * @param spiceLevel  nullable – only for FOOD
     * @param volumeMl    nullable – only for BEVERAGE
     * @return concrete MenuItem subclass (never null)
     */
    public MenuItem create(Long id, Long categoryId, String name, String description,
                           BigDecimal price, ItemType itemType, boolean available,
                           SpiceLevel spiceLevel, Integer volumeMl) {
        // OOP: Polymorphism – different subclass returned for each type
        return switch (itemType) {
            case FOOD     -> new Food(id, categoryId, name, description, price, available, spiceLevel);
            case BEVERAGE -> new Beverage(id, categoryId, name, description, price, available, volumeMl);
            case DESSERT  -> new Dessert(id, categoryId, name, description, price, available);
        };
    }
}
