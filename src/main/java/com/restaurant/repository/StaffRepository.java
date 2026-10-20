package com.restaurant.repository;

import com.restaurant.model.User;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 * Data access contract for the staff table (CHEF / WAITER / MANAGER).
 */
public interface StaffRepository extends Repository<User, Long> {

    Optional<User> findByUsername(String username);

    @Override
    Optional<User> findById(Long id);

    @Override
    List<User> findAll();
}
