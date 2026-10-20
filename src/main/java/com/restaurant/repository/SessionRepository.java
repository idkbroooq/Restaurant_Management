package com.restaurant.repository;

import com.restaurant.enums.SessionStatus;
import com.restaurant.model.DiningSession;

import java.util.List;
import java.util.Optional;

/**
 * // OOP: Interface, DAO Pattern, Generics
 * Data access contract for dining_session (a customer's visit at a table).
 */
public interface SessionRepository extends Repository<DiningSession, Long> {

    @Override
    Optional<DiningSession> findById(Long id);

    @Override
    List<DiningSession> findAll();

    /** Finds the active (ACTIVE) session for a table, if any. */
    Optional<DiningSession> findOpenSessionByTableId(Long tableId);

    DiningSession save(DiningSession session);

    /** Closes (COMPLETED / CANCELLED) a session and stamps end_time. */
    void updateStatus(Long sessionId, SessionStatus status);
}
