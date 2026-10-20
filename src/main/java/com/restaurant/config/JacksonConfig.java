package com.restaurant.config;

import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * // OOP: Encapsulation, Separation of Concerns
 * Serialises every java.time.LocalDateTime as an ISO-8601 UTC string
 * ("2026-10-19T02:45:00Z"), exactly as the docs/openapi.yaml contract specifies
 * ("All timestamps are formatted as ISO-8601 UTC strings").
 *
 * The JVM default time zone is pinned to UTC at start-up (see RestaurantApplication),
 * so LocalDateTime.now() values really are UTC.
 */
@Configuration
public class JacksonConfig {

    /** Second-precision UTC formatter shared by every java.time value we expose. */
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer isoUtcTimestamps() {
        return builder -> builder.serializers(new LocalDateTimeSerializer(ISO_UTC));
    }
}
