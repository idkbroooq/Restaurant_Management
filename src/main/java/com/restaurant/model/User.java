package com.restaurant.model;

import com.restaurant.enums.StaffRole;

import java.util.Objects;

/**
 * // OOP: Abstract class, Inheritance
 * Abstract base for all restaurant staff. Concrete subclasses are Chef, Waiter, Manager.
 * Role-specific behaviour is enforced through getRole() (Abstraction + Polymorphism).
 */
public abstract class User {

    // // OOP: Encapsulation – private fields; no public setters
    private final Long id;
    private final String username;
    private final String passwordHash; // BCrypt hash; never stored in plain text
    private final String fullName;
    private final boolean active;

    protected User(Long id, String username, String passwordHash, String fullName, boolean active) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash must not be blank");
        }
        this.id = id;
        this.username = username.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.active = active;
    }

    /**
     * // OOP: Abstraction – each subclass declares its own role.
     */
    public abstract StaffRole getRole();

    // Getters (Encapsulation)
    public Long getId()             { return id; }
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName()     { return fullName; }
    public boolean isActive()       { return active; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + ", username='" + username
                + "', role=" + getRole() + "}";
    }
}
