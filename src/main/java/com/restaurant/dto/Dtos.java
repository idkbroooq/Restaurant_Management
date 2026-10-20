package com.restaurant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All REST request/response payloads, defined as Java records so they are
 * immutable and self-describing. Every field name mirrors docs/openapi.yaml
 * exactly (single source of truth for the API contract).
 *
 * // OOP: Encapsulation via records (immutable data carriers)
 */
public final class Dtos {

    private Dtos() {} // utility holder — never instantiated

    // ======================================================================
    // Requests
    // ======================================================================

    /** POST /api/auth/login */
    public record LoginRequest(
            @NotBlank(message = "username is required") String username,
            @NotBlank(message = "password is required") String password) {}

    /** POST /api/orders — carries NO prices (server reads them from the DB). */
    public record CreateOrderRequest(
            @NotBlank(message = "tableToken is required") String tableToken,
            String sessionToken,
            @Size(max = 255, message = "specialNotes must be at most 255 characters") String specialNotes,
            @NotEmpty(message = "items must contain at least one line") @Valid List<OrderItemRequest> items) {}

    /** A single line inside CreateOrderRequest. */
    public record OrderItemRequest(
            @NotNull(message = "itemId is required") Long itemId,
            @NotNull(message = "quantity is required")
            @Min(value = 1, message = "quantity must be at least 1")
            @Max(value = 20, message = "quantity must be at most 20") Integer quantity,
            @Size(max = 255, message = "itemNotes must be at most 255 characters") String itemNotes) {}

    /** PATCH /api/orders/{id}/status — optimistic concurrency via {from,to}. */
    public record UpdateOrderStatusRequest(
            @NotBlank(message = "from is required") String from,
            @NotBlank(message = "to is required") String to,
            @Size(max = 255, message = "notes must be at most 255 characters") String notes) {}

    /** POST /api/sessions/{id}/pay */
    public record PaymentRequest(
            @NotBlank(message = "paymentMethod is required") String paymentMethod,
            @NotNull(message = "amount is required")
            @DecimalMin(value = "0.01", message = "amount must be greater than 0") BigDecimal amount,
            @Size(max = 100, message = "transactionRef must be at most 100 characters") String transactionRef) {}

    /** POST /api/admin/menu and PUT /api/admin/menu/{id} */
    public record CreateMenuItemRequest(
            @NotNull(message = "categoryId is required") Long categoryId,
            @NotBlank(message = "name is required") @Size(max = 100, message = "name must be at most 100 characters") String name,
            String description,
            @NotNull(message = "price is required")
            @DecimalMin(value = "0.01", message = "price must be greater than 0") BigDecimal price,
            @NotBlank(message = "itemType is required") String itemType,
            String spiceLevel,
            Integer volumeMl,
            Boolean isAvailable) {}

    /** POST /api/admin/categories */
    public record CreateCategoryRequest(
            @NotBlank(message = "name is required") @Size(max = 50, message = "name must be at most 50 characters") String name,
            @Size(max = 255, message = "description must be at most 255 characters") String description,
            Integer displayOrder,
            Boolean isActive) {}

    /** POST /api/admin/tables and PUT /api/admin/tables/{id} */
    public record CreateTableRequest(
            @NotBlank(message = "tableNumber is required") @Size(max = 20, message = "tableNumber must be at most 20 characters") String tableNumber,
            @NotNull(message = "capacity is required") @Min(value = 1, message = "capacity must be at least 1") Integer capacity,
            Boolean isActive) {}

    /** PATCH /api/admin/menu/{id}/availability */
    public record AvailabilityRequest(
            @NotNull(message = "isAvailable is required") Boolean isAvailable) {}

    // ======================================================================
    // Responses
    // ======================================================================

    /** GET /api/tables/{token} */
    public record TableSessionResponse(Long tableId, String tableNumber, Integer capacity,
                                       String qrToken, boolean isActive, DiningSessionDto session) {}

    public record DiningSessionDto(Long sessionId, String sessionToken, String status,
                                   LocalDateTime startTime, LocalDateTime endTime) {}

    /** A menu catalog entry (also nested inside MenuCategoryWithItems). */
    public record MenuItemDto(Long itemId, Long categoryId, String categoryName, String name,
                              String description, BigDecimal price, String itemType,
                              String spiceLevel, Integer volumeMl, boolean isAvailable,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {}

    /** GET /api/menu — one row per category with its available items. */
    public record MenuCategoryWithItems(Long categoryId, String name, String description,
                                        Integer displayOrder, List<MenuItemDto> items) {}

    public record CategoryDto(Long categoryId, String name, String description,
                              Integer displayOrder, boolean isActive) {}

    public record RestaurantTableDto(Long tableId, String tableNumber, Integer capacity,
                                     String qrToken, boolean isActive, Long currentSessionId,
                                     String currentSessionStatus) {}

    /** A line item as returned inside an order. */
    public record OrderItemDetail(Long orderItemId, Long itemId, String name, String itemType,
                                  String spiceLevel, Integer volumeMl, Integer quantity,
                                  BigDecimal unitPrice, BigDecimal subtotal, String itemNotes) {}

    /** Full order view (POST /api/orders, GET /api/orders/{id}, PATCH status). */
    public record OrderResponse(Long orderId, Long sessionId, String tableNumber, String status,
                                BigDecimal totalAmount, String specialNotes,
                                LocalDateTime createdAt, LocalDateTime updatedAt,
                                List<OrderItemDetail> items) {}

    /** Compact order row used in lists/bills. */
    public record OrderSummaryResponse(Long orderId, Long sessionId, String status,
                                       BigDecimal totalAmount, Integer itemCount,
                                       LocalDateTime createdAt) {}

    /** GET /api/kitchen/orders */
    public record KitchenOrderTicket(Long orderId, String tableNumber, String status,
                                     LocalDateTime orderPlacedAt, Integer elapsedMinutes,
                                     String specialNotes, List<OrderItemDetail> items) {}

    /**
     * Staff identity embedded in login response / GET /api/auth/me.
     * Serializable so it can be stored safely in the HTTP session.
     */
    public record StaffProfile(Long staffId, String username, String fullName, String role)
            implements java.io.Serializable {}

    /** POST /api/auth/login */
    public record LoginResponse(String token, StaffProfile staff) {}

    /** POST /api/sessions/{id}/bill */
    public record BillResponse(Long sessionId, String tableNumber, String sessionStatus,
                               Integer activeOrderCount, BigDecimal subtotal,
                               BigDecimal taxPercentage, BigDecimal taxAmount,
                               BigDecimal grandTotal, List<OrderSummaryResponse> orders) {}

    /** POST /api/sessions/{id}/pay */
    public record PaymentResponse(Long paymentId, Long sessionId, BigDecimal amount,
                                  String paymentMethod, String paymentStatus,
                                  String transactionRef, LocalDateTime paidAt,
                                  String sessionStatus) {}

    /** GET /api/admin/tables/{id}/qr */
    public record TableQrResponse(Long tableId, String tableNumber, String qrToken,
                                  String menuUrl, String qrCodeDataUrl) {}

    /** Generic single-field acknowledgement. */
    public record MessageResponse(String message) {}

    // ---- Report rows ----

    public record DailySalesRow(String salesDate, Integer totalTicketsPlaced,
                                Integer completedTickets, Integer cancelledTickets,
                                BigDecimal dailyGrossRevenue, BigDecimal avgTicketSize) {}

    public record TopItemRow(Long itemId, String itemName, String category, String itemType,
                             Integer totalUnitsSold, BigDecimal totalRevenueGenerated) {}

    public record BusiestTableRow(Long tableId, String tableNumber, Integer capacity,
                                  Integer sessionsHosted, Integer totalOrdersPlaced,
                                  BigDecimal grossRevenueEarned) {}

    public record AvgPrepTimeResponse(Integer ordersMeasured, BigDecimal avgKitchenPrepMinutes,
                                      Integer fastestPrepMinutes, Integer slowestPrepMinutes) {}
}
