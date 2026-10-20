package com.restaurant.repository;

import com.restaurant.model.Category;
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
 * // OOP: DAO Pattern – JDBC implementation for category table.
 * All SQL in prepared statements, no string concatenation (Rule 1).
 */
@Repository
public class JdbcCategoryRepository implements CategoryRepository {

    private final JdbcTemplate jdbc;

    public JdbcCategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SQL_FIND_ALL =
            "SELECT category_id, name, description, display_order, is_active " +
            "FROM category ORDER BY display_order, name";

    private static final String SQL_FIND_BY_ID =
            "SELECT category_id, name, description, display_order, is_active " +
            "FROM category WHERE category_id = ?";

    private static final String SQL_INSERT =
            "INSERT INTO category (name, description, display_order, is_active) VALUES (?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE category SET name=?, description=?, display_order=?, is_active=? WHERE category_id=?";

    private final RowMapper<Category> mapper = (rs, rowNum) -> new Category(
            rs.getLong("category_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getInt("display_order"),
            rs.getBoolean("is_active")
    );

    @Override
    public List<Category> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }

    @Override
    public Optional<Category> findById(Long id) {
        List<Category> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Category save(Category category) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, category.getName());
            ps.setString(2, category.getDescription());
            ps.setInt(3, category.getDisplayOrder());
            ps.setBoolean(4, category.isActive());
            return ps;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public void update(Category category) {
        jdbc.update(SQL_UPDATE,
                category.getName(), category.getDescription(),
                category.getDisplayOrder(), category.isActive(), category.getId());
    }
}
