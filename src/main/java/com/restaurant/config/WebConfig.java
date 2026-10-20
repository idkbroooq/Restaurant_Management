package com.restaurant.config;

import com.restaurant.enums.StaffRole;
import com.restaurant.repository.TableRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * // OOP: Composition, Dependency Injection, Interface implementation (WebMvcConfigurer)
 * Registers the security interceptors with their path patterns:
 *
 *   /api/auth/me, /api/auth/logout          -> any authenticated staff
 *   /api/kitchen/**, /api/orders/*&#47;status -> CHEF or MANAGER
 *   /api/sessions/**                        -> WAITER or MANAGER
 *   /api/admin/**                           -> MANAGER
 *   /api/tables/**                          -> valid (active) table QR token
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TableRepository tableRepo;

    public WebConfig(TableRepository tableRepo) {
        this.tableRepo = tableRepo;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Authenticated staff only (any role) — used by "who am I" / logout.
        registry.addInterceptor(new StaffAuthInterceptor())
                .addPathPatterns("/api/auth/me", "/api/auth/logout");

        // Kitchen board + ticket status transitions.
        registry.addInterceptor(new StaffAuthInterceptor(StaffRole.CHEF, StaffRole.MANAGER))
                .addPathPatterns("/api/kitchen/**", "/api/orders/*/status");

        // Billing + payment settlement (front-of-house staff).
        registry.addInterceptor(new StaffAuthInterceptor(StaffRole.WAITER, StaffRole.MANAGER))
                .addPathPatterns("/api/sessions/**");

        // Back-office administration.
        registry.addInterceptor(new StaffAuthInterceptor(StaffRole.MANAGER))
                .addPathPatterns("/api/admin/**");

        // Customer: the QR token must resolve to an active table.
        registry.addInterceptor(new TableTokenInterceptor(tableRepo))
                .addPathPatterns("/api/tables/**");
    }

    /**
     * Spring Boot's welcome page only covers the root, so a request for "/customer/" would
     * fall through to static-resource handling and 404. These view controllers give every
     * UI folder a directory URL.
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/customer/").setViewName("forward:/customer/index.html");
        registry.addViewController("/kitchen/").setViewName("forward:/kitchen/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }
}
