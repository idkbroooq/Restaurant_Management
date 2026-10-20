package com.restaurant.repository;

import com.restaurant.enums.ItemType;
import com.restaurant.enums.SpiceLevel;
import com.restaurant.factory.MenuItemFactory;
import com.restaurant.model.Category;
import com.restaurant.model.MenuCategoryGroup;
import com.restaurant.model.MenuItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * // OOP: DAO Pattern, Factory Pattern integration, Polymorphism (RowMapper)
 * JDBC DAO for menu_item. Delegates subtype construction to {@link MenuItemFactory}
 * so a DB row's item_type becomes the correct Food / Beverage / Dessert object.
 * SQL lives only here, prepared statements only (Rule 1).
 */
@Repository
public class JdbcMenuItemRepository implements MenuItemRepository {

    private final JdbcTemplate jdbc;
    private final MenuItemFactory factory; // OOP: Composition + Factory

    // OOP: Factory + RowMapper – delegates subtype creation to MenuItemFactory.
    // Assigned in the constructor because it captures the injected factory.
    private final RowMapper<MenuItem> mapper;

    public JdbcMenuItemRepository(JdbcTemplate jdbc, MenuItemFactory factory) {
        this.jdbc = jdbc;
        this.factory = factory;
        this.mapper = (rs, rowNum) -> factory.create(
                rs.getLong("item_id"),
                rs.getLong("category_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBigDecimal("price"),
                ItemType.valueOf(rs.getString("item_type")),
                rs.getBoolean("is_available"),
                toSpiceLevel(rs.getString("spice_level")),
                toNullableInt(rs, "volume_ml"));
    }

    private static final String COLS =
            "item_id, category_id, name, description, price, item_type, " +
            "is_available, spice_level, volume_ml";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLS + " FROM menu_item WHERE is_deleted = FALSE ORDER BY category_id, name";

    private static final String SQL_FIND_AVAILABLE =
            "SELECT " + COLS + " FROM menu_item WHERE is_available = TRUE AND is_deleted = FALSE ORDER BY name";

    private static final String SQL_FIND_BY_CATEGORY =
            "SELECT " + COLS + " FROM menu_item WHERE category_id = ? AND is_available = TRUE AND is_deleted = FALSE ORDER BY name";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLS + " FROM menu_item WHERE item_id = ? AND is_deleted = FALSE";

    // ONE grouped query: active categories LEFT JOINed to their available items.
    // Item columns are aliased with an "item_" prefix to avoid clashing with the
    // category columns of the same name (name / description).
    private static final String SQL_MENU_GROUPED =
            "SELECT c.category_id, c.name AS category_name, c.description AS category_description, " +
            "       c.display_order, c.is_active, " +
            "       mi.item_id, mi.name AS item_name, mi.description AS item_description, " +
            "       mi.price AS item_price, mi.item_type AS item_type, " +
            "       mi.is_available AS item_available, mi.spice_level AS item_spice_level, " +
            "       mi.volume_ml AS item_volume_ml " +
            "FROM category c " +
            "LEFT JOIN menu_item mi ON mi.category_id = c.category_id " +
            "     AND mi.is_available = TRUE AND mi.is_deleted = FALSE " +
            "WHERE c.is_active = TRUE " +
            "ORDER BY c.display_order, c.name, mi.name";

    private static final String SQL_INSERT =
            "INSERT INTO menu_item (category_id, name, description, price, item_type, " +
            "is_available, spice_level, volume_ml) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE =
            "UPDATE menu_item SET category_id = ?, name = ?, description = ?, price = ?, " +
            "item_type = ?, is_available = ?, spice_level = ?, volume_ml = ? " +
            "WHERE item_id = ? AND is_deleted = FALSE";

    private static final String SQL_UPDATE_AVAILABILITY =
            "UPDATE menu_item SET is_available = ? WHERE item_id = ? AND is_deleted = FALSE";

    private static final String SQL_SOFT_DELETE =
            "UPDATE menu_item SET is_deleted = TRUE, is_available = FALSE WHERE item_id = ?";

    @Override
    public List<MenuItem> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }

    @Override
    public List<MenuItem> findAllAvailable() {
        return jdbc.query(SQL_FIND_AVAILABLE, mapper);
    }

    @Override
    public List<MenuItem> findByCategory(Long categoryId) {
        return jdbc.query(SQL_FIND_BY_CATEGORY, mapper, categoryId);
    }

    @Override
    public Optional<MenuItem> findById(Long id) {
        List<MenuItem> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * // OOP: Generics/Collections – single query result is regrouped into
     * MenuCategoryGroup records in Java (no N+1 lookups per category).
     */
    @Override
    public List<MenuCategoryGroup> findMenuGroupedByCategory() {
        Map<Long, Category> categories = new LinkedHashMap<>();
        Map<Long, List<MenuItem>> itemsByCategory = new LinkedHashMap<>();

        jdbc.query(SQL_MENU_GROUPED, rs -> {
            long categoryId = rs.getLong("category_id");
            categories.putIfAbsent(categoryId, new Category(
                    categoryId,
                    rs.getString("category_name"),
                    rs.getString("category_description"),
                    rs.getInt("display_order"),
                    rs.getBoolean("is_active")));

            List<MenuItem> bucket = itemsByCategory.computeIfAbsent(categoryId, id -> new ArrayList<>());
            // LEFT JOIN yields a NULL item_id for a category with no available items
            if (rs.getObject("item_id") != null) {
                String spiceRaw = rs.getString("item_spice_level");
                int volumeRaw = rs.getInt("item_volume_ml");
                bucket.add(factory.create(
                        rs.getLong("item_id"),
                        categoryId,
                        rs.getString("item_name"),
                        rs.getString("item_description"),
                        rs.getBigDecimal("item_price"),
                        ItemType.valueOf(rs.getString("item_type")),
                        rs.getBoolean("item_available"),
                        spiceRaw != null ? SpiceLevel.valueOf(spiceRaw) : null,
                        rs.wasNull() ? null : volumeRaw));
            }
        });

        List<MenuCategoryGroup> groups = new ArrayList<>();
        categories.forEach((id, category) ->
                groups.add(new MenuCategoryGroup(category, itemsByCategory.get(id))));
        return groups;
    }

    @Override
    public MenuItem save(MenuItem item) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, item.getCategoryId());
            ps.setString(2, item.getName());
            ps.setString(3, item.getDescription());
            ps.setBigDecimal(4, item.getPrice());
            ps.setString(5, item.getItemType().name());
            ps.setBoolean(6, item.isAvailable());
            if (item.getSpiceLevel() != null) {
                ps.setString(7, item.getSpiceLevel().name());
            } else {
                ps.setNull(7, java.sql.Types.VARCHAR);
            }
            if (item.getVolumeMl() != null) {
                ps.setInt(8, item.getVolumeMl());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            return ps;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public void update(MenuItem item) {
        jdbc.update(SQL_UPDATE,
                item.getCategoryId(), item.getName(), item.getDescription(), item.getPrice(),
                item.getItemType().name(), item.isAvailable(),
                item.getSpiceLevel() != null ? item.getSpiceLevel().name() : null,
                item.getVolumeMl(), item.getId());
    }

    @Override
    public void updateAvailability(Long id, boolean available) {
        jdbc.update(SQL_UPDATE_AVAILABILITY, available, id);
    }

    @Override
    public void softDelete(Long id) {
        jdbc.update(SQL_SOFT_DELETE, id);
    }

    // --- small NULL-safe helpers ---

    private static SpiceLevel toSpiceLevel(String raw) {
        return raw != null ? SpiceLevel.valueOf(raw) : null;
    }

    private static Integer toNullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
