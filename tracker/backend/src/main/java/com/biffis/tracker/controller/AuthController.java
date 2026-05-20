package com.biffis.tracker.controller;

import com.biffis.tracker.dto.LoginRequest;
import com.biffis.tracker.dto.LoginResponse;
import com.biffis.tracker.dto.UserView;
import com.biffis.tracker.security.CurrentUser;
import com.biffis.tracker.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password());
    }

    /**
     * Returns the currently-authenticated user. Useful for the frontend to
     * rehydrate session state from a stored token. Requires a valid JWT
     * (it's not under the permit-all matcher).
     */
    @GetMapping("/me")
    public UserView me() {
        var current = CurrentUser.get();
        return new UserView(current.id(), current.username(), null, null);
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "invalid_credentials"));
    }
}
