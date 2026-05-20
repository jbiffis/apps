package com.biffis.tracker.config;

import com.biffis.tracker.security.JwtAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. Only {@code /api/auth/**} and the health check are
 * public; everything else requires a valid Bearer token. Unauthenticated
 * requests to protected endpoints get a bare 401 (no redirect, no
 * WWW-Authenticate challenge that would pop a browser basic-auth dialog).
 *
 * Gated on web mode so the {@code set-password} CLI (which runs
 * {@code WebApplicationType.NONE}) doesn't try to build a servlet filter chain.
 */
@Configuration
@ConditionalOnWebApplication
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Only login + health are public. /api/auth/me (and any
                        // future authed auth-routes) require a valid token.
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
