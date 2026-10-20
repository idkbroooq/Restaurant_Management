package com.restaurant.repository;

import com.restaurant.model.Category;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 */
public interface CategoryRepository extends Repository<Category, Long> {

    @Override
    List<Category> findAll();

    @Override
    Optional<Category> findById(Long id);

    Category save(Category category);

    void update(Category category);
}
