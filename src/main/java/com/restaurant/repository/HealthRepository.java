package com.restaurant.repository;

/**
 * // OOP: Interface Abstraction, DAO pattern
 * Data Access Object contract for health and connectivity checks.
 */
public interface HealthRepository {
    boolean isDatabaseHealthy();
}
