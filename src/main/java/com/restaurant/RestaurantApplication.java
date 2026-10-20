package com.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * Main entry point for the Restaurant Table-Ordering System.
 * Technology Stack: Java 17, Spring Boot 3, Spring JDBC (JdbcTemplate), MySQL 8.
 */
@SpringBootApplication
public class RestaurantApplication {

    public static void main(String[] args) {
        // Pin the JVM default zone to UTC so every LocalDateTime.now() is UTC and can
        // be serialised with a trailing 'Z' (docs/openapi.yaml: "ISO-8601 UTC strings").
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(RestaurantApplication.class, args);
    }
}
