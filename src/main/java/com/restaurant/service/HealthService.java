package com.restaurant.service;

import com.restaurant.dto.HealthResponseDto;

/**
 * // OOP: Interface Abstraction
 * Service contract for system health evaluation.
 */
public interface HealthService {
    HealthResponseDto checkHealth();
}
