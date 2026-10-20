package com.restaurant.repository;

import com.restaurant.enums.PaymentMethodType;
import com.restaurant.enums.PaymentStatus;
import com.restaurant.model.Payment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * // OOP: DAO Pattern
 * JDBC DAO for the payment table. All SQL here, prepared statements (Rule 1).
 * Money always BigDecimal (Rule 3).
 *
 * DB payment columns: payment_id, session_id, order_id, amount, payment_method,
 * payment_status, transaction_ref, paid_at, created_at. The stored `amount` is the
 * settled total; subtotal/tax/discount are recomputed in the billing service.
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcTemplate jdbc;

    public JdbcPaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SQL_FIND_BY_ID =
            "SELECT payment_id, session_id, amount, payment_method, payment_status, " +
            "transaction_ref, paid_at, created_at FROM payment WHERE payment_id = ?";

    private static final String SQL_FIND_ALL =
            "SELECT payment_id, session_id, amount, payment_method, payment_status, " +
            "transaction_ref, paid_at, created_at FROM payment ORDER BY created_at DESC";

    private static final String SQL_FIND_BY_SESSION =
            "SELECT payment_id, session_id, amount, payment_method, payment_status, " +
            "transaction_ref, paid_at, created_at FROM payment WHERE session_id = ? " +
            "ORDER BY payment_id DESC LIMIT 1";

    private static final String SQL_INSERT =
            "INSERT INTO payment (session_id, amount, payment_method, payment_status, transaction_ref) " +
            "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE payment SET payment_status = ?, transaction_ref = ?, paid_at = NOW() WHERE payment_id = ?";

    private final RowMapper<Payment> mapper = (rs, rowNum) -> {
        Timestamp paidTs = rs.getTimestamp("paid_at");
        BigDecimal amount = rs.getBigDecimal("amount");
        return new Payment(
                rs.getLong("payment_id"),
                rs.getLong("session_id"),
                amount,                       // subtotal (best-effort; recomputed in service)
                BigDecimal.ZERO,              // tax not persisted separately
                BigDecimal.ZERO,              // discount not persisted separately
                amount,                       // totalAmount == stored amount
                PaymentMethodType.valueOf(rs.getString("payment_method")),
                PaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getString("transaction_ref"),
                rs.getTimestamp("created_at").toLocalDateTime(),
                paidTs != null ? paidTs.toLocalDateTime() : null
        );
    };

    @Override
    public Optional<Payment> findById(Long id) {
        List<Payment> results = jdbc.query(SQL_FIND_BY_ID, mapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Payment> findAll() {
        return jdbc.query(SQL_FIND_ALL, mapper);
    }

    @Override
    public Optional<Payment> findBySessionId(Long sessionId) {
        List<Payment> results = jdbc.query(SQL_FIND_BY_SESSION, mapper, sessionId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Payment save(Payment payment) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, payment.getSessionId());
            ps.setBigDecimal(2, payment.getTotalAmount()); // Rule 3: BigDecimal
            ps.setString(3, payment.getMethod().name());
            ps.setString(4, payment.getStatus().name());
            ps.setString(5, payment.getTransactionRef());
            return ps;
        }, keys);
        return findById(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public void updateStatus(Long paymentId, PaymentStatus status, String transactionRef) {
        jdbc.update(SQL_UPDATE_STATUS, status.name(), transactionRef, paymentId);
    }
}
