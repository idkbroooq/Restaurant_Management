package com.restaurant.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * // OOP: Type-Safe Enums, State Pattern Transition Logic
 * Represents the lifecycle stages of an order ticket.
 * Enforces valid linear state transitions:
 * PLACED -> ACCEPTED -> PREPARING -> READY -> SERVED
 * (with CANCELLED available from non-terminal states).
 */
public enum OrderStatus {

    PLACED {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            return EnumSet.of(ACCEPTED, CANCELLED);
        }
    },
    ACCEPTED {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            return EnumSet.of(PREPARING, CANCELLED);
        }
    },
    PREPARING {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            return EnumSet.of(READY, CANCELLED);
        }
    },
    READY {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            return EnumSet.of(SERVED, CANCELLED);
        }
    },
    SERVED {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            // Terminal state
            return Collections.emptySet();
        }
    },
    CANCELLED {
        @Override
        public Set<OrderStatus> getNextStatuses() {
            // Terminal state
            return Collections.emptySet();
        }
    };

    /**
     * Returns the set of valid statuses this state can transition into.
     * // OOP: Abstraction & Polymorphism within Enum constants
     *
     * @return unmodifiable set of allowed next OrderStatus values
     */
    public abstract Set<OrderStatus> getNextStatuses();

    /**
     * Evaluates whether a transition from this status to target status is valid.
     *
     * @param next the intended subsequent status
     * @return true if transition is legally allowed or idempotent; false otherwise
     */
    public boolean canTransitionTo(OrderStatus next) {
        if (next == null) {
            return false;
        }
        if (this == next) {
            return true; // Idempotent transition is permitted
        }
        return getNextStatuses().contains(next);
    }

    /**
     * Checks if this status is terminal (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == SERVED || this == CANCELLED;
    }
}
