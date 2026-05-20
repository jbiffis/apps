package com.biffis.tracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Convenience accessor for the authenticated user's id. Service methods that
 * touch user-scoped data call {@code CurrentUser.id()} and pass the result
 * into every repository query — this is how per-user isolation is enforced.
 *
 * Throws if there is no authenticated user, which should never happen behind
 * the JWT filter chain (those requests are rejected before reaching a service).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser authUser)) {
            throw new IllegalStateException("No authenticated user in context");
        }
        return authUser;
    }

    public static UUID id() {
        return get().id();
    }
}
