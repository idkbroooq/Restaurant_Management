package com.restaurant.repository;

import com.restaurant.enums.SessionStatus;
import com.restaurant.model.DiningSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * // OOP: DAO Pattern
 * JDBC DAO for dining_session table. All SQL here, prepared statements (Rule 1).
 */
@Repository
public class JdbcSessionRepository implements SessionRepository {

    private final JdbcTemplate jdbc;

    public JdbcSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String COLS =
            "session_id, table_id, session_token, status, start_time, end_time";

    private static final String SQL_FIND_BY_ID =
            "SELECT " + COLS + " FROM dining_session WHERE session_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT " + COLS + " FROM dining_session ORDER BY start_time DESC";

    private static final String SQL_FIND_OPEN_BY_TABLE =
            "SELECT " + COLS + " FROM dining_session WHERE table_id = ? AND status = 'ACTIVE' LIMIT 1";

    private static final String SQL_INSERT =
            "INSERT INTO dining_session (table_id, session_token, status) VALUES (?, ?, ?)";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE dining_session SET status = ?, " +
            "end_time = CASE WHEN ? IN ('COMPLETED','CANCELLED') THEN NOW() ELSE end_time END " +
            "WHERE session_id = ?";

    private final RowMapper<DiningSession> mapper = (rs, rowNum) -> {
        Timestamp endTs = rs.getTimestamp("end_time");
        return new DiningSession(
                rs.getLong("session_id"),
                rs.getLong("table_id"),
                rs.getString("session_token"),
                SessionStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("start_time").toLocalDateTime(),
                endTs != null ? endTs.toLocalDateTime() : null
        );
    };

    @Override
    public Optional<DiningSession> findById(Long id) {
        List<DiningSession> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<DiningSession> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }

    @Override
    public Optional<DiningSession> findOpenSessionByTableId(Long tableId) {
        List<DiningSession> results = jdbc.query(SQL_FIND_OPEN_BY_TABLE, mapper, tableId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public DiningSession save(DiningSession session) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, session.getTableId());
            ps.setString(2, session.getSessionToken());
            ps.setString(3, session.getStatus().name());
            return ps;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public void updateStatus(Long sessionId, SessionStatus status) {
        jdbc.update(SQL_UPDATE_STATUS, status.name(), status.name(), sessionId);
    }
}
