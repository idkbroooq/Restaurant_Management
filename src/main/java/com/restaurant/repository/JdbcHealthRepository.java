package com.restaurant.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * // OOP: Polymorphism, Dependency Inversion, DAO pattern
 * Spring JDBC implementation of HealthRepository.
 * Rule 1: SQL lives only in repository classes, prepared statements only.
 */
@Repository
public class JdbcHealthRepository implements HealthRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcHealthRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public JdbcHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isDatabaseHealthy() {
        try {
            // Rule 1: Prepared statement execution; verifies MySQL connectivity
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Database health check query failed: {}", e.getMessage());
            return false;
        }
    }
}
