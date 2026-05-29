package com.biffis.tracker.service;

import com.biffis.tracker.dto.LoginResponse;
import com.biffis.tracker.dto.UserView;
import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.UserRepository;
import com.biffis.tracker.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Login: verify email + password, issue a JWT. Wrong email and wrong
 * password are intentionally indistinguishable to the caller — both throw
 * {@link InvalidCredentialsException}, which the controller renders as a
 * generic 401. Never logs the attempted email or password.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(String email, String rawPassword) {
        User user = users.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.issue(user.getId(), user.getEmail());
        return new LoginResponse(token, UserView.of(user), user.isMustChangePassword());
    }

    /**
     * Change the authenticated user's password. Verifies the current password,
     * stores the new hash, clears the force-change flag, and issues a fresh
     * token so the client drops the now-stale one.
     */
    public LoginResponse changePassword(UUID userId, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        users.save(user);
        String token = jwtService.issue(user.getId(), user.getEmail());
        return new LoginResponse(token, UserView.of(user), false);
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("invalid_credentials");
        }
    }
}
