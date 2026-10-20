package com.restaurant.repository;

import com.restaurant.dto.Dtos;
import com.restaurant.model.RestaurantTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * // OOP: DAO Pattern, Encapsulation
 * JDBC implementation of TableRepository.
 * All SQL in this class only (Rule 1). Uses prepared statements only (Rule 1).
 */
@Repository // OOP: marks this as a Spring-managed DAO
public class JdbcTableRepository implements TableRepository {

    private final JdbcTemplate jdbc;

    // OOP: Constructor injection (Dependency Inversion Principle)
    public JdbcTableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // SQL: Rule 1 – all queries live here, prepared statements, no string concatenation
    private static final String COLS =
            "table_id, table_number, qr_token, capacity, is_active, created_at";

    private static final String SQL_FIND_BY_TOKEN =
            "SELECT " + COLS + " FROM restaurant_table WHERE qr_token = ?";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLS + " FROM restaurant_table WHERE table_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLS + " FROM restaurant_table ORDER BY table_number";

    // ONE query for the admin table list: table LEFT JOIN its current ACTIVE session.
    private static final String SQL_FIND_ALL_WITH_SESSION =
            "SELECT rt.table_id, rt.table_number, rt.qr_token, rt.capacity, rt.is_active, " +
            "       ds.session_id AS current_session_id, ds.status AS current_session_status " +
            "FROM restaurant_table rt " +
            "LEFT JOIN dining_session ds ON ds.table_id = rt.table_id AND ds.status = 'ACTIVE' " +
            "ORDER BY rt.table_number";

    private static final String SQL_INSERT =
            "INSERT INTO restaurant_table (table_number, qr_token, capacity, is_active) " +
            "VALUES (?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE restaurant_table SET table_number=?, capacity=?, is_active=? WHERE table_id=?";

    private static final String SQL_TABLE_OF_SESSION =
            "SELECT rt.table_number FROM dining_session ds " +
            "JOIN restaurant_table rt ON rt.table_id = ds.table_id WHERE ds.session_id = ?";

    // OOP: Encapsulation – mapper is private, only used within this class
    private final RowMapper<RestaurantTable> mapper = (rs, rowNum) -> new RestaurantTable(
            rs.getLong("table_id"),
            rs.getString("table_number"),
            rs.getString("qr_token"),
            rs.getInt("capacity"),
            rs.getBoolean("is_active"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    @Override
    public Optional<RestaurantTable> findByToken(String qrToken) {
        List<RestaurantTable> results = jdbc.query(SQL_FIND_BY_TOKEN, mapper, qrToken);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<RestaurantTable> findById(Long id) {
        List<RestaurantTable> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<RestaurantTable> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }

    @Override
    public List<Dtos.RestaurantTableDto> findAllWithSession() {
        return jdbc.query(SQL_FIND_ALL_WITH_SESSION, (rs, rowNum) -> {
            Long sessionId = rs.getObject("current_session_id") != null
                    ? rs.getLong("current_session_id") : null;
            return new Dtos.RestaurantTableDto(
                    rs.getLong("table_id"),
                    rs.getString("table_number"),
                    rs.getInt("capacity"),
                    rs.getString("qr_token"),
                    rs.getBoolean("is_active"),
                    sessionId,
                    rs.getString("current_session_status"));
        });
    }

    @Override
    public RestaurantTable save(RestaurantTable table) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, table.getTableNumber());
            ps.setString(2, table.getQrToken());
            ps.setInt(3, table.getCapacity());
            ps.setBoolean(4, table.isActive());
            return ps;
        }, keys);
        Long newId = keys.getKey().longValue();
        return findById(newId).orElseThrow();
    }

    @Override
    public void update(RestaurantTable table) {
        jdbc.update(SQL_UPDATE,
                table.getTableNumber(), table.getCapacity(), table.isActive(), table.getId());
    }

    @Override
    public Optional<String> findTableNumberBySessionId(Long sessionId) {
        List<String> results = jdbc.query(SQL_TABLE_OF_SESSION,
                (rs, rowNum) -> rs.getString("table_number"), sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
