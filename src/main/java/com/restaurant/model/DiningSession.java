package com.restaurant.model;

import com.restaurant.enums.SessionStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * // OOP: Encapsulation
 * A dining session represents the lifecycle of customers sitting at a table:
 * from when they scan the QR code (ACTIVE) until they pay and leave (COMPLETED).
 * A session may contain multiple orders.
 *
 * DB columns: session_id, table_id, session_token, status, start_time, end_time
 */
public class DiningSession {

    private final Long id;
    private final Long tableId;
    private final String sessionToken;  // unique UUID-based token
    private SessionStatus status;       // ACTIVE | COMPLETED | CANCELLED
    private final LocalDateTime startTime;
    private LocalDateTime endTime;      // null until session ends

    public DiningSession(Long id, Long tableId, String sessionToken,
                         SessionStatus status, LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.tableId = tableId;
        this.sessionToken = sessionToken;
        this.status = status != null ? status : SessionStatus.ACTIVE;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public boolean isActive() { return status == SessionStatus.ACTIVE; }

    /** Called after payment is confirmed. */
    public void complete() {
        if (status == SessionStatus.COMPLETED) return; // idempotent
        this.status = SessionStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
    }

    /** Called if session is abandoned without payment. */
    public void cancel() {
        this.status = SessionStatus.CANCELLED;
        this.endTime = LocalDateTime.now();
    }

    public Long getId()              { return id; }
    public Long getTableId()         { return tableId; }
    public String getSessionToken()  { return sessionToken; }
    public SessionStatus getStatus() { return status; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime()   { return endTime; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiningSession that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "DiningSession{id=" + id + ", tableId=" + tableId
                + ", status=" + status + "}";
    }
}
