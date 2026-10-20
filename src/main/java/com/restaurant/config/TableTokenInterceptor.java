package com.restaurant.config;

import com.restaurant.exception.TableNotFoundException;
import com.restaurant.model.RestaurantTable;
import com.restaurant.repository.TableRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * // OOP: Interface implementation (HandlerInterceptor), Dependency Inversion
 * Customer endpoints carry the table's QR token in the path (e.g. /api/tables/{token}).
 * This interceptor rejects any request whose token does not resolve to an ACTIVE table
 * before the controller (and therefore any SQL) runs.
 *
 * POST /api/orders carries the token in the JSON body instead; there the token is
 * validated by {@code OrderService.placeOrder} (same 404 semantics).
 */
public class TableTokenInterceptor implements HandlerInterceptor {

    private static final String PATH_SEGMENT = "tables";

    private final TableRepository tableRepo;

    public TableTokenInterceptor(TableRepository tableRepo) {
        this.tableRepo = tableRepo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = extractToken(request.getRequestURI(), request.getContextPath());
        if (token == null || token.isBlank()) {
            throw new TableNotFoundException("(missing token)");
        }
        tableRepo.findByToken(token)
                .filter(RestaurantTable::isActive)
                .orElseThrow(() -> new TableNotFoundException(token));
        return true;
    }

    /** Pulls the segment that follows "/tables/" out of the request URI. */
    private static String extractToken(String uri, String contextPath) {
        String path = contextPath != null && !contextPath.isEmpty() ? uri.substring(contextPath.length()) : uri;
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length - 1; i++) {
            if (PATH_SEGMENT.equals(segments[i])) {
                return segments[i + 1];
            }
        }
        return null;
    }
}
