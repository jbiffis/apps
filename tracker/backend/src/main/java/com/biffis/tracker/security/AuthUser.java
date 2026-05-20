package com.biffis.tracker.security;

import java.util.UUID;

/**
 * The authenticated principal stored in the SecurityContext. Carries only
 * what the JWT proves: the user id and username. Anything else (gender,
 * display name) is looked up from the DB when a service needs it.
 */
public record AuthUser(UUID id, String username) {
}
