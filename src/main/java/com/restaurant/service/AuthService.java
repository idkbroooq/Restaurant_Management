package com.restaurant.service;

import com.restaurant.dto.Dtos;
import com.restaurant.exception.AuthenticationException;
import com.restaurant.model.User;
import com.restaurant.repository.StaffRepository;
import com.restaurant.util.ApiMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * // OOP: Layers, Encapsulation (password hash never leaves the model)
 * Staff authentication using BCrypt. Credential validation only — the HTTP
 * session (creation, role checks, invalidation) is owned by the controller layer.
 */
@Service
public class AuthService {

    /** HttpSession attribute key holding the logged-in {@link Dtos.StaffProfile}. */
    public static final String STAFF_SESSION_KEY = "staff";

    private final StaffRepository staffRepo;
    private final BCryptPasswordEncoder bcrypt; // spring-security-crypto only (no web filters)

    public AuthService(StaffRepository staffRepo) {
        this.staffRepo = staffRepo;
        this.bcrypt = new BCryptPasswordEncoder();
    }

    /**
     * Verifies credentials and returns the caller's profile.
     *
     * @throws AuthenticationException when the username is unknown, inactive, or the password is wrong.
     */
    public Dtos.StaffProfile authenticate(String username, String password) {
        User user = staffRepo.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        if (!user.isActive()) {
            throw new AuthenticationException("Account is disabled: " + username);
        }
        if (!bcrypt.matches(password, user.getPasswordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }
        return ApiMapper.toStaffProfile(user);
    }
}
