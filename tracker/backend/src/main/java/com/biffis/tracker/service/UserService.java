package com.biffis.tracker.service;

import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.UserRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * User lookups. Split out from AuthService (Epic 2 note) now that the catalog
 * needs the current user's gender for audience filtering.
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    public User requireById(UUID id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("user not found"));
    }

    public User currentUser() {
        return requireById(CurrentUser.id());
    }

    /** Current user's gender ('male'/'female'/'other'/null) — drives audience filtering. */
    public String currentGender() {
        return currentUser().getGender();
    }
}
