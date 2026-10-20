package com.restaurant.controller;

import com.restaurant.dto.Dtos;
import com.restaurant.exception.AuthenticationException;
import com.restaurant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * // OOP: Layers (controller -> service), Encapsulation
 * Staff authentication. A successful login stores the {@link Dtos.StaffProfile}
 * in the HTTP session; the session id doubles as the token returned to the client
 * (the contract exposes CookieAuth = JSESSIONID).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Staff login, session validation, and logout")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Staff login", description = "Authenticates staff and opens an HTTP session.")
    public ResponseEntity<Dtos.LoginResponse> login(@Valid @RequestBody Dtos.LoginRequest request,
                                                    HttpServletRequest http) {
        Dtos.StaffProfile staff = authService.authenticate(request.username(), request.password());

        // Create the HTTP session and remember who is logged in.
        HttpSession session = http.getSession(true);
        session.setAttribute(AuthService.STAFF_SESSION_KEY, staff);

        return ResponseEntity.ok(new Dtos.LoginResponse(session.getId(), staff));
    }

    @PostMapping("/logout")
    @Operation(summary = "Staff logout", description = "Invalidates the current HTTP session.")
    public ResponseEntity<Dtos.MessageResponse> logout(HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(new Dtos.MessageResponse("Logged out successfully."));
    }

    @GetMapping("/me")
    @Operation(summary = "Current staff profile")
    public ResponseEntity<Dtos.StaffProfile> me(HttpSession session) {
        return ResponseEntity.ok(currentStaff(session));
    }

    /** Reads the logged-in profile from the session (interceptor guarantees it is present). */
    public static Dtos.StaffProfile currentStaff(HttpSession session) {
        Object attr = session == null ? null : session.getAttribute(AuthService.STAFF_SESSION_KEY);
        if (!(attr instanceof Dtos.StaffProfile staff)) {
            throw new AuthenticationException("Not authenticated.");
        }
        return staff;
    }
}
