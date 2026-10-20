package com.restaurant.dto;

import java.time.LocalDateTime;

/**
 * Health response data transfer object matching OpenAPI contract.
 * The timestamp is a UTC LocalDateTime so it is serialised as
 * "2026-10-19T02:45:00Z" (see JacksonConfig).
 */
public class HealthResponseDto {
    private String status;
    private String database;
    private LocalDateTime timestamp;

    public HealthResponseDto() {}

    public HealthResponseDto(String status, String database, LocalDateTime timestamp) {
        this.status = status;
        this.database = database;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
