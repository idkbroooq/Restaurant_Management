package com.restaurant.service;

import com.restaurant.dto.HealthResponseDto;
import com.restaurant.repository.HealthRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * // OOP: Polymorphism, Separation of Concerns
 * Layer 5: controller -> service -> repository -> DB.
 */
@Service
public class HealthServiceImpl implements HealthService {

    private final HealthRepository healthRepository;

    public HealthServiceImpl(HealthRepository healthRepository) {
        this.healthRepository = healthRepository;
    }

    @Override
    public HealthResponseDto checkHealth() {
        boolean dbHealthy = healthRepository.isDatabaseHealthy();
        String status = dbHealthy ? "UP" : "DEGRADED";
        String dbStatus = dbHealthy ? "CONNECTED" : "DISCONNECTED";
        return new HealthResponseDto(status, dbStatus, LocalDateTime.now(ZoneOffset.UTC));
    }
}
