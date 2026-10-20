package com.restaurant.config;

import com.restaurant.dto.Dtos;
import com.restaurant.enums.StaffRole;
import com.restaurant.exception.AuthenticationException;
import com.restaurant.exception.ForbiddenException;
import com.restaurant.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * // OOP: Interface implementation (HandlerInterceptor), Enums, Custom Exceptions
 * Guards staff-only endpoints. It requires the HTTP session created at login to
 * carry a {@link Dtos.StaffProfile} and (optionally) restricts access to a set of roles.
 *
 *   new StaffAuthInterceptor()                          -> any authenticated staff
 *   new StaffAuthInterceptor(CHEF, MANAGER)             -> /api/kitchen/**
 *   new StaffAuthInterceptor(WAITER, MANAGER)           -> /api/sessions/**
 *   new StaffAuthInterceptor(MANAGER)                   -> /api/admin/**
 *
 * Thrown exceptions are translated by {@link GlobalExceptionHandler} (401 / 403).
 */
public class StaffAuthInterceptor implements HandlerInterceptor {

    private final Set<StaffRole> allowedRoles; // empty == any authenticated staff

    public StaffAuthInterceptor(StaffRole... allowedRoles) {
        this.allowedRoles = allowedRoles.length == 0
                ? EnumSet.noneOf(StaffRole.class)
                : EnumSet.copyOf(Arrays.asList(allowedRoles));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        HttpSession session = request.getSession(false);
        Object attr = session == null ? null : session.getAttribute(AuthService.STAFF_SESSION_KEY);

        if (!(attr instanceof Dtos.StaffProfile staff)) {
            throw new AuthenticationException("Staff authentication required for " + request.getRequestURI());
        }

        if (!allowedRoles.isEmpty() && !allowedRoles.contains(StaffRole.valueOf(staff.role()))) {
            throw new ForbiddenException(String.format(
                    "Role '%s' is not permitted to access %s (requires one of %s).",
                    staff.role(), request.getRequestURI(), allowedRoles));
        }
        return true;
    }
}
