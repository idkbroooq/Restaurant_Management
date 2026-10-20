package com.restaurant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * // OOP: Encapsulation, Separation of Concerns
 * Makes the generated OpenAPI document (GET /api-docs, rendered by /swagger-ui.html)
 * match the header of docs/openapi.yaml: same title, version, contact and server list.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI restaurantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Restaurant Table-Ordering System API")
                        .version("1.0.0")
                        .description("""
                                Single Source of Truth OpenAPI contract for the Restaurant Table-Ordering System.

                                ### Architectural Guidelines
                                - **Client Pricing Rule**: `POST /api/orders` carries **NO prices**. Prices are strictly retrieved from the database.
                                - **Financial Precision**: Money attributes are formatted as JSON numbers with 2 decimal places.
                                - **Time Standard**: All timestamps are formatted as ISO-8601 UTC strings (`YYYY-MM-DDTHH:mm:ssZ`).
                                - **Enum Conformity**: All enum strings match the MySQL 8 schema enums exactly.
                                - **State Transitions**: `PATCH /api/orders/{id}/status` enforces optimistic concurrency (`409 Conflict` if `current != from`).
                                """)
                        .contact(new Contact().name("Restaurant Engineering Team").email("dev@restaurant.local")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Local Spring Boot Development Server")));
    }
}
