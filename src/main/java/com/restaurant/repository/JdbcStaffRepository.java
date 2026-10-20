package com.restaurant.repository;

import com.restaurant.enums.StaffRole;
import com.restaurant.model.Chef;
import com.restaurant.model.Manager;
import com.restaurant.model.User;
import com.restaurant.model.Waiter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: DAO Pattern, Factory-like construction of User subtypes
 * JDBC DAO for the staff table. Maps CHEF/WAITER/MANAGER to concrete subclasses.
 */
@Repository
public class JdbcStaffRepository implements StaffRepository {

    private final JdbcTemplate jdbc;

    public JdbcStaffRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS =
            "staff_id, username, password_hash, full_name, role, is_active";

    private static final String SQL_FIND_BY_USERNAME =
            "SELECT " + COLS + " FROM staff WHERE username = ? AND is_active = TRUE";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLS + " FROM staff WHERE staff_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLS + " FROM staff ORDER BY staff_id";

    // OOP: Polymorphism – builds the correct User subclass based on role column
    private final RowMapper<User> mapper = (rs, rowNum) -> {
        Long id = rs.getLong("staff_id");
        String username = rs.getString("username");
        String hash = rs.getString("password_hash");
        String fullName = rs.getString("full_name");
        boolean active = rs.getBoolean("is_active");
        StaffRole role = StaffRole.valueOf(rs.getString("role"));
        return switch (role) {
            case CHEF    -> new Chef(id, username, hash, fullName, active);
            case WAITER  -> new Waiter(id, username, hash, fullName, active);
            case MANAGER -> new Manager(id, username, hash, fullName, active);
        };
    };

    @Override
    public Optional<User> findByUsername(String username) {
        List<User> results = jdbc.query(SQL_FIND_BY_USERNAME, mapper, username);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<User> findById(Long id) {
        List<User> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<User> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }
}
