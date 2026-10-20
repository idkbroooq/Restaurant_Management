package com.restaurant.model;

import com.restaurant.enums.StaffRole;

/**
 * // OOP: Inheritance – concrete User; Polymorphism – getRole() returns MANAGER
 */
public class Manager extends User {

    public Manager(Long id, String username, String passwordHash, String fullName, boolean active) {
        super(id, username, passwordHash, fullName, active);
    }

    @Override
    public StaffRole getRole() { return StaffRole.MANAGER; }
}
