package com.biffis.tracker.dto;

import com.biffis.tracker.model.User;

import java.util.UUID;

/**
 * Public projection of a {@link User} — never includes the password hash.
 */
public record UserView(UUID id, String email, String displayName, String gender) {

    public static UserView of(User user) {
        return new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getGender());
    }
}
