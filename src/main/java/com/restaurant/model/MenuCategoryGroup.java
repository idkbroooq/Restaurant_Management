package com.restaurant.model;

import java.util.List;

/**
 * // OOP: Composition, Generics/Collections
 * A category plus its (available) menu items, produced by the repository in a
 * single grouped query so the customer menu never triggers N+1 lookups.
 */
public record MenuCategoryGroup(Category category, List<MenuItem> items) {}
