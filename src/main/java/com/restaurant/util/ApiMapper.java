package com.restaurant.util;

import com.restaurant.dto.Dtos;
import com.restaurant.model.Category;
import com.restaurant.model.DiningSession;
import com.restaurant.model.KitchenTicket;
import com.restaurant.model.MenuItem;
import com.restaurant.model.Order;
import com.restaurant.model.OrderItem;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.RestaurantTable;
import com.restaurant.model.User;

import java.util.List;

/**
 * // OOP: Polymorphism (dispatch on getItemType()/getFinalPrice()), Encapsulation
 * Converts domain objects into the API DTO records defined in docs/openapi.yaml.
 * Kept in one place so every service produces byte-identical response shapes.
 */
public final class ApiMapper {

    private ApiMapper() {}

    public static Dtos.OrderItemDetail toItemDetail(OrderItem i) {
        return new Dtos.OrderItemDetail(
                i.getId(),
                i.getMenuItemId(),
                i.getMenuItemName(),
                i.getItemType() != null ? i.getItemType().name() : null,
                i.getSpiceLevel() != null ? i.getSpiceLevel().name() : null,
                i.getVolumeMl(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getLineTotal(),               // stored generated column equivalent
                i.getSpecialInstructions());
    }

    public static Dtos.OrderResponse toOrderResponse(Order order, String tableNumber) {
        List<Dtos.OrderItemDetail> items = order.getItems().stream()
                .map(ApiMapper::toItemDetail)
                .toList();
        return new Dtos.OrderResponse(
                order.getId(),
                order.getSessionId(),
                tableNumber,
                order.getStatus().name(),
                order.calculateSubtotalOrZero(),
                order.getSpecialNotes(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items);
    }

    public static Dtos.OrderSummaryResponse toOrderSummary(OrderSummary s) {
        return new Dtos.OrderSummaryResponse(
                s.orderId(),
                s.sessionId(),
                s.status().name(),
                s.totalAmount(),
                s.itemCount(),
                s.createdAt());
    }

    public static Dtos.KitchenOrderTicket toKitchenTicket(KitchenTicket t) {
        List<Dtos.OrderItemDetail> items = t.getItems().stream()
                .map(ApiMapper::toItemDetail)
                .toList();
        return new Dtos.KitchenOrderTicket(
                t.getOrderId(),
                t.getTableNumber(),
                t.getStatus().name(),
                t.getOrderPlacedAt(),
                t.getElapsedMinutes(),
                t.getSpecialNotes(),
                items);
    }

    /**
     * // OOP: Polymorphism – getFinalPrice() is a virtual call resolved at runtime by the
     * concrete MenuItem subclass (Food / Beverage / Dessert). Every subclass currently
     * returns the catalog price read from the database (Rule 2), so the price a customer
     * sees is always exactly `menu_item.price`.
     */
    public static Dtos.MenuItemDto toMenuItemDto(MenuItem m, String categoryName) {
        return new Dtos.MenuItemDto(
                m.getId(),
                m.getCategoryId(),
                categoryName,
                m.getName(),
                m.getDescription(),
                m.getFinalPrice(),
                m.getItemType().name(),
                m.getSpiceLevel() != null ? m.getSpiceLevel().name() : null,
                m.getVolumeMl(),
                m.isAvailable(),
                null,   // createdAt not tracked on the domain model
                null);  // updatedAt not tracked on the domain model
    }

    public static Dtos.CategoryDto toCategoryDto(Category c) {
        return new Dtos.CategoryDto(
                c.getId(), c.getName(), c.getDescription(), c.getDisplayOrder(), c.isActive());
    }

    public static Dtos.StaffProfile toStaffProfile(User u) {
        return new Dtos.StaffProfile(u.getId(), u.getUsername(), u.getFullName(), u.getRole().name());
    }

    public static Dtos.DiningSessionDto toSessionDto(DiningSession s) {
        return new Dtos.DiningSessionDto(
                s.getId(), s.getSessionToken(), s.getStatus().name(), s.getStartTime(), s.getEndTime());
    }

    public static Dtos.TableSessionResponse toTableSession(RestaurantTable t, DiningSession session) {
        return new Dtos.TableSessionResponse(
                t.getId(), t.getTableNumber(), t.getCapacity(), t.getQrToken(), t.isActive(),
                session != null ? toSessionDto(session) : null);
    }

    /** Admin table row incl. the id/status of the table's current ACTIVE session. */
    public static Dtos.RestaurantTableDto toTableDto(RestaurantTable t, Long currentSessionId,
                                                     String currentSessionStatus) {
        return new Dtos.RestaurantTableDto(
                t.getId(), t.getTableNumber(), t.getCapacity(), t.getQrToken(), t.isActive(),
                currentSessionId, currentSessionStatus);
    }
}
