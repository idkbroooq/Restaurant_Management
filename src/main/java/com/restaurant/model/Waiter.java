package com.restaurant.model;

import com.restaurant.enums.StaffRole;

/**
 * // OOP: Inheritance – concrete User; Polymorphism – getRole() returns WAITER
 */
public class Waiter extends User {

    public Waiter(Long id, String username, String passwordHash, String fullName, boolean active) {
        super(id, username, passwordHash, fullName, active);
    }

    @Override
    public StaffRole getRole() { return StaffRole.WAITER; }
}
