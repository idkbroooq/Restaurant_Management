package com.restaurant.config;

import com.restaurant.strategy.DiscountStrategy;
import com.restaurant.strategy.NoDiscount;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * // OOP: Strategy Pattern wiring (Dependency Inversion)
 * Supplies the default {@link DiscountStrategy} used by BillingService.
 * Swap this bean (send in HappyHourDiscount / PercentageDiscount) to change
 * promotional behaviour without touching the service (Open/Closed Principle).
 */
@Configuration
public class AppConfig {

    @Bean
    public DiscountStrategy discountStrategy() {
        return new NoDiscount();
    }
}
