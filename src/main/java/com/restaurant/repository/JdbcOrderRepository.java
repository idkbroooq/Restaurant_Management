package com.restaurant.repository;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.OrderStatus;
import com.restaurant.enums.SpiceLevel;
import com.restaurant.model.BillTotals;
import com.restaurant.model.KitchenTicket;
import com.restaurant.model.Order;
import com.restaurant.model.OrderItem;
import com.restaurant.model.OrderSummary;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * // OOP: DAO Pattern, Transaction Management
 * JDBC DAO for orders + order_item tables.
 * save() runs inside ONE database transaction (Rule 4).
 * All SQL in prepared statements (Rule 1). Never reads price from client (Rule 2).
 */
@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // --- Order SQL ---
    private static final String SQL_ORDER_COLS =
            "o.order_id, o.session_id, o.status, o.special_notes, o.created_at, o.updated_at";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + SQL_ORDER_COLS + " FROM orders o WHERE o.order_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + SQL_ORDER_COLS + " FROM orders o ORDER BY o.created_at DESC";

    private static final String SQL_FIND_BY_SESSION =
            "SELECT " + SQL_ORDER_COLS + " FROM orders o WHERE o.session_id = ? ORDER BY o.created_at";

    private static final String SQL_INSERT_ORDER =
            "INSERT INTO orders (session_id, status, total_amount, special_notes) VALUES (?, ?, ?, ?)";

    /**
     * Optimistic-concurrency update (Rule: detect concurrent kitchen clicks).
     * Only updates when the row still holds the caller's expected 'from' status.
     */
    private static final String SQL_UPDATE_STATUS =
            "UPDATE orders SET status = ? WHERE order_id = ? AND status = ?";

    // --- OrderItem SQL ---
    private static final String SQL_ITEM_COLS =
            "oi.order_item_id, oi.order_id, oi.item_id, mi.name, mi.item_type, " +
            "mi.spice_level, mi.volume_ml, oi.unit_price, oi.quantity, oi.item_notes";

    private static final String SQL_FIND_ITEMS =
            "SELECT " + SQL_ITEM_COLS +
            " FROM order_item oi JOIN menu_item mi ON oi.item_id = mi.item_id WHERE oi.order_id = ?";

    private static final String SQL_INSERT_ITEM =
            "INSERT INTO order_item (order_id, item_id, quantity, unit_price, item_notes) " +
            "VALUES (?, ?, ?, ?, ?)";

    // --- Kitchen view (v_kitchen_active_orders) ---
    private static final String SQL_KITCHEN_VIEW =
            "SELECT v.order_id, v.table_number, v.order_status, v.order_placed_at, v.elapsed_minutes, " +
            "       v.order_item_id, v.item_name, v.item_type, v.spice_level, v.volume_ml, " +
            "       v.quantity, v.item_notes, v.order_special_notes, oi.item_id, oi.unit_price " +
            "FROM v_kitchen_active_orders v " +
            "JOIN order_item oi ON oi.order_item_id = v.order_item_id " +
            "ORDER BY v.order_placed_at ASC, v.order_id ASC, v.order_item_id ASC";

    // --- Order summaries from v_order_totals (item count computed in SQL) ---
    private static final String SQL_SUMMARIES_BY_SESSION =
            "SELECT o.order_id, o.session_id, o.status, o.total_amount, o.created_at, " +
            "       vt.line_item_count " +
            "FROM orders o JOIN v_order_totals vt ON vt.order_id = o.order_id " +
            "WHERE o.session_id = ? ORDER BY o.created_at";

    // --- Table number of an order (via its dining session) ---
    private static final String SQL_TABLE_OF_ORDER =
            "SELECT rt.table_number FROM orders o " +
            "JOIN dining_session ds ON ds.session_id = o.session_id " +
            "JOIN restaurant_table rt ON rt.table_id = ds.table_id WHERE o.order_id = ?";

    private final RowMapper<Order> orderMapper = (rs, rowNum) -> {
        Timestamp updatedTs = rs.getTimestamp("updated_at");
        return new Order(
                rs.getLong("order_id"),
                rs.getLong("session_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getString("special_notes"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                updatedTs != null ? updatedTs.toLocalDateTime() : null
        );
    };

    private final RowMapper<OrderItem> itemMapper = (rs, rowNum) ->
            new OrderItem(
                    rs.getLong("order_item_id"),
                    rs.getLong("order_id"),
                    rs.getLong("item_id"),
                    rs.getString("name"),
                    ItemType.valueOf(rs.getString("item_type")),
                    toSpiceLevel(rs.getString("spice_level")),
                    toNullableInt(rs, "volume_ml"),
                    rs.getBigDecimal("unit_price"),   // Rule 2: price from DB, not client
                    rs.getInt("quantity"),
                    rs.getString("item_notes"),
                    null // order_item has no created_at column
            );

    @Override
    public Optional<Order> findById(Long id) {
        List<Order> results = jdbc.query(SQL_FIND_BY_ID, orderMapper, id);
        if (results.isEmpty()) return Optional.empty();
        Order order = results.get(0);
        loadItems(order);
        return Optional.of(order);
    }

    @Override
    public List<Order> findAll() {
        List<Order> orders = jdbc.query(SQL_FIND_ALL, orderMapper);
        loadItemsForOrders(orders);
        return orders;
    }

    @Override
    public List<Order> findBySessionId(Long sessionId) {
        List<Order> orders = jdbc.query(SQL_FIND_BY_SESSION, orderMapper, sessionId);
        loadItemsForOrders(orders);
        return orders;
    }

    /**
     * Builds the kitchen queue from the {@code v_kitchen_active_orders} view.
     * The view yields one row per dish; rows are regrouped into one KitchenTicket
     * per order (single query – no N+1).
     */
    @Override
    public List<KitchenTicket> findActiveForKitchen() {
        Map<Long, KitchenTicket> tickets = new LinkedHashMap<>();

        jdbc.query(SQL_KITCHEN_VIEW, rs -> {
            long orderId = rs.getLong("order_id");
            KitchenTicket ticket = tickets.get(orderId);
            if (ticket == null) {
                ticket = new KitchenTicket(
                        orderId,
                        rs.getString("table_number"),
                        OrderStatus.valueOf(rs.getString("order_status")),
                        rs.getTimestamp("order_placed_at").toLocalDateTime(),
                        rs.getInt("elapsed_minutes"),
                        rs.getString("order_special_notes"));
                tickets.put(orderId, ticket);
            }
            ticket.addItem(new OrderItem(
                    rs.getLong("order_item_id"),
                    orderId,
                    rs.getLong("item_id"),
                    rs.getString("item_name"),
                    ItemType.valueOf(rs.getString("item_type")),
                    toSpiceLevel(rs.getString("spice_level")),
                    toNullableInt(rs, "volume_ml"),
                    rs.getBigDecimal("unit_price"),
                    rs.getInt("quantity"),
                    rs.getString("item_notes"),
                    null));
        });

        return new ArrayList<>(tickets.values());
    }

    @Override
    public List<OrderSummary> findSummariesBySessionId(Long sessionId) {
        return jdbc.query(SQL_SUMMARIES_BY_SESSION, (rs, rowNum) -> new OrderSummary(
                rs.getLong("order_id"),
                rs.getLong("session_id"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("total_amount"),
                rs.getInt("line_item_count"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), sessionId);
    }

    /**
     * Calls the {@code sp_generate_bill} stored procedure (subtotal + 5% GST).
     * The procedure returns a single-row result set.
     */
    @Override
    public Optional<BillTotals> generateBill(Long sessionId) {
        List<BillTotals> results = jdbc.query("CALL sp_generate_bill(?)", (rs, rowNum) -> new BillTotals(
                rs.getLong("session_id"),
                rs.getString("table_number"),
                rs.getString("session_status"),
                rs.getInt("active_order_count"),
                rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("tax_percentage"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("grand_total")
        ), sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * // OOP: Transaction (Rule 4) – inserts the order header AND all order_item rows atomically.
     * The unit_price carried by each OrderItem was read from the DB by the service (Rule 2).
     */
    @Override
    @Transactional // Spring manages COMMIT / ROLLBACK
    public Order save(Order order) {
        BigDecimal total = order.getItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT_ORDER, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, order.getSessionId());
            ps.setString(2, order.getStatus().name());
            ps.setBigDecimal(3, total);
            ps.setString(4, order.getSpecialNotes());
            return ps;
        }, keys);

        long orderId = keys.getKey().longValue();

        for (OrderItem item : order.getItems()) {
            jdbc.update(SQL_INSERT_ITEM,
                    orderId, item.getMenuItemId(), item.getQuantity(),
                    item.getUnitPrice(), item.getSpecialInstructions());
        }

        return findById(orderId).orElseThrow();
    }

    /**
     * Optimistic-concurrency status update. Runs the "SET @current_staff_id" session
     * variable and the guarded UPDATE on the SAME connection so the DB audit trigger
     * (trg_orders_log_status_change) records the acting staff id.
     *
     * @return rows changed: 1 = success, 0 = somebody already moved the ticket
     */
    @Override
    public int updateStatus(Long orderId, OrderStatus from, OrderStatus to, Long changedByStaffId) {
        return jdbc.execute((ConnectionCallback<Integer>) con -> {
            if (changedByStaffId != null) {
                try (PreparedStatement ps = con.prepareStatement("SET @current_staff_id = ?")) {
                    ps.setLong(1, changedByStaffId);
                    ps.execute();
                }
            }
            try (PreparedStatement ps = con.prepareStatement(SQL_UPDATE_STATUS)) {
                ps.setString(1, to.name());
                ps.setLong(2, orderId);
                ps.setString(3, from.name());
                int changed = ps.executeUpdate();
                // Clear the session variable so it cannot leak to a later request on this pooled connection.
                try (Statement st = con.createStatement()) {
                    st.execute("SET @current_staff_id = NULL");
                }
                return changed;
            }
        });
    }

    @Override
    public Optional<String> findTableNumberByOrderId(Long orderId) {
        List<String> results = jdbc.query(SQL_TABLE_OF_ORDER,
                (rs, rowNum) -> rs.getString("table_number"), orderId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // --- Private helpers ---

    private void loadItems(Order order) {
        List<OrderItem> items = jdbc.query(SQL_FIND_ITEMS, itemMapper, order.getId());
        order.setItemsFromDb(items);
    }

    /**
     * Loads the order_item rows for MANY orders in ONE query (avoids N+1).
     * Items are grouped by order id and attached to the matching Order.
     */
    private void loadItemsForOrders(List<Order> orders) {
        if (orders.isEmpty()) return;
        List<Long> ids = orders.stream().map(Order::getId).toList();
        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT " + SQL_ITEM_COLS +
                " FROM order_item oi JOIN menu_item mi ON oi.item_id = mi.item_id " +
                "WHERE oi.order_id IN (" + placeholders + ")";

        Map<Long, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();
        for (OrderItem item : jdbc.query(sql, itemMapper, ids.toArray())) {
            itemsByOrder.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
        }
        for (Order order : orders) {
            order.setItemsFromDb(itemsByOrder.getOrDefault(order.getId(), List.of()));
        }
    }

    private static SpiceLevel toSpiceLevel(String raw) {
        return raw != null ? SpiceLevel.valueOf(raw) : null;
    }

    private static Integer toNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
