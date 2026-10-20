package com.restaurant.repository;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Generics, Interface Abstraction, DAO Pattern
 * Generic contract shared by every data-access object in the system.
 *
 * {@code T} is the domain entity and {@code ID} is the primary-key type,
 * so the same shape is reused for MenuItem/Long, Category/Long, Role/Integer… 
 * Concrete implementations (JdbcXxxRepository) supply the SQL.
 */
public interface Repository<T, ID> {

    /** Loads a single entity by primary key, or empty if absent. */
    Optional<T> findById(ID id);

    /** Loads every non-deleted entity of this type. */
    List<T> findAll();
}
