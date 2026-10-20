package com.restaurant.repository;

import com.restaurant.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * // OOP: DAO Pattern
 * Read-only JDBC implementation backing the admin dashboard reports.
 * Each query mirrors the analytical SQL in db/advanced.sql. No business logic here.
 */
@Repository
public class JdbcReportRepository implements ReportRepository {

    private final JdbcTemplate jdbc;

    public JdbcReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Query 6: revenue per day (optional date-range filter bound via placeholders)
    private static final String SQL_DAILY_SALES_BASE =
            "SELECT DATE(o.created_at) AS sales_date, " +
            "       COUNT(o.order_id) AS total_tickets_placed, " +
            "       SUM(CASE WHEN o.status <> 'CANCELLED' THEN 1 ELSE 0 END) AS completed_tickets, " +
            "       SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tickets, " +
            "       IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS daily_gross_revenue, " +
            "       IFNULL(ROUND(AVG(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE NULL END), 2), 0.00) AS avg_ticket_size " +
            "FROM orders o WHERE 1 = 1";

    // Query 5: top selling items (limit is a bound parameter)
    private static final String SQL_TOP_ITEMS =
            "SELECT mi.item_id, mi.name AS item_name, c.name AS category, mi.item_type, " +
            "       SUM(oi.quantity) AS total_units_sold, SUM(oi.subtotal) AS total_revenue_generated " +
            "FROM order_item oi " +
            "JOIN menu_item mi ON oi.item_id = mi.item_id " +
            "JOIN category c ON mi.category_id = c.category_id " +
            "JOIN orders o ON oi.order_id = o.order_id " +
            "WHERE o.status <> 'CANCELLED' " +
            "GROUP BY mi.item_id, mi.name, c.name, mi.item_type " +
            "ORDER BY total_units_sold DESC, total_revenue_generated DESC LIMIT ?";

    // Query 9: busiest tables
    private static final String SQL_BUSIEST_TABLES =
            "SELECT rt.table_id, rt.table_number, rt.capacity, " +
            "       COUNT(DISTINCT ds.session_id) AS sessions_hosted, " +
            "       COUNT(o.order_id) AS total_orders_placed, " +
            "       IFNULL(SUM(CASE WHEN o.status <> 'CANCELLED' THEN o.total_amount ELSE 0.00 END), 0.00) AS gross_revenue_earned " +
            "FROM restaurant_table rt " +
            "LEFT JOIN dining_session ds ON rt.table_id = ds.table_id " +
            "LEFT JOIN orders o ON ds.session_id = o.session_id " +
            "GROUP BY rt.table_id, rt.table_number, rt.capacity " +
            "ORDER BY gross_revenue_earned DESC, total_orders_placed DESC";

    // Query 7: average preparation time from the status log
    private static final String SQL_AVG_PREP_TIME =
            "SELECT COUNT(o.order_id) AS orders_measured, " +
            "       IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 2), 0.00) AS avg_kitchen_prep_minutes, " +
            "       IFNULL(MIN(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 0) AS fastest_prep_minutes, " +
            "       IFNULL(MAX(TIMESTAMPDIFF(MINUTE, l_acc.created_at, l_ready.created_at)), 0) AS slowest_prep_minutes " +
            "FROM orders o " +
            "JOIN order_status_log l_acc ON o.order_id = l_acc.order_id AND l_acc.to_status = 'ACCEPTED' " +
            "JOIN order_status_log l_ready ON o.order_id = l_ready.order_id AND l_ready.to_status = 'READY'";

    @Override
    public List<Dtos.DailySalesRow> dailySales(LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder(SQL_DAILY_SALES_BASE);
        List<Object> params = new ArrayList<>();
        if (startDate != null) {
            sql.append(" AND DATE(o.created_at) >= ?");
            params.add(Date.valueOf(startDate));
        }
        if (endDate != null) {
            sql.append(" AND DATE(o.created_at) <= ?");
            params.add(Date.valueOf(endDate));
        }
        sql.append(" GROUP BY DATE(o.created_at) ORDER BY sales_date DESC");

        return jdbc.query(sql.toString(), (rs, rowNum) -> new Dtos.DailySalesRow(
                rs.getString("sales_date"),
                rs.getInt("total_tickets_placed"),
                rs.getInt("completed_tickets"),
                rs.getInt("cancelled_tickets"),
                rs.getBigDecimal("daily_gross_revenue"),
                rs.getBigDecimal("avg_ticket_size")
        ), params.toArray());
    }

    @Override
    public List<Dtos.TopItemRow> topItems(int limit) {
        return jdbc.query(SQL_TOP_ITEMS, (rs, rowNum) -> new Dtos.TopItemRow(
                rs.getLong("item_id"),
                rs.getString("item_name"),
                rs.getString("category"),
                rs.getString("item_type"),
                rs.getInt("total_units_sold"),
                rs.getBigDecimal("total_revenue_generated")
        ), limit);
    }

    @Override
    public List<Dtos.BusiestTableRow> busiestTables() {
        return jdbc.query(SQL_BUSIEST_TABLES, (rs, rowNum) -> new Dtos.BusiestTableRow(
                rs.getLong("table_id"),
                rs.getString("table_number"),
                rs.getInt("capacity"),
                rs.getInt("sessions_hosted"),
                rs.getInt("total_orders_placed"),
                rs.getBigDecimal("gross_revenue_earned")
        ));
    }

    @Override
    public Dtos.AvgPrepTimeResponse avgPrepTime() {
        return jdbc.queryForObject(SQL_AVG_PREP_TIME, (rs, rowNum) -> {
            BigDecimal avg = rs.getBigDecimal("avg_kitchen_prep_minutes");
            return new Dtos.AvgPrepTimeResponse(
                    rs.getInt("orders_measured"),
                    avg != null ? avg : BigDecimal.ZERO,
                    rs.getInt("fastest_prep_minutes"),
                    rs.getInt("slowest_prep_minutes"));
        });
    }
}
