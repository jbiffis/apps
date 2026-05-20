package com.biffis.tracker.service;

import com.biffis.tracker.dto.LoginResponse;
import com.biffis.tracker.dto.UserView;
import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.UserRepository;
import com.biffis.tracker.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Login: verify username + password, issue a JWT. Wrong username and wrong
 * password are intentionally indistinguishable to the caller — both throw
 * {@link InvalidCredentialsException}, which the controller renders as a
 * generic 401. Never logs the attempted username or password.
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

    public LoginResponse login(String username, String rawPassword) {
        User user = users.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        String token = jwtService.issue(user.getId(), user.getUsername());
        return new LoginResponse(token, UserView.of(user));
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("invalid_credentials");
        }
    }
}
