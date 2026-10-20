package com.restaurant.repository;

import com.restaurant.dto.Dtos;
import com.restaurant.model.RestaurantTable;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics, Abstraction
 * Data Access Object contract for restaurant_table operations.
 * SQL lives ONLY in the implementing class (Rule 1).
 */
public interface TableRepository extends Repository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByToken(String qrToken);

    @Override
    Optional<RestaurantTable> findById(Long id);

    @Override
    List<RestaurantTable> findAll();

    /**
     * Admin list: every table LEFT JOINed with its current ACTIVE session in ONE
     * query (no N+1), exposing currentSessionId / currentSessionStatus.
     */
    List<Dtos.RestaurantTableDto> findAllWithSession();

    RestaurantTable save(RestaurantTable table);

    void update(RestaurantTable table);

    /** Resolves the table number (e.g. "T01") that hosts a given dining session. */
    Optional<String> findTableNumberBySessionId(Long sessionId);
}
