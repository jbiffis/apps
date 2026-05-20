package com.biffis.tracker.repository;

import com.biffis.tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Case-insensitive: usernames are matched regardless of capitalization. */
    Optional<User> findByUsernameIgnoreCase(String username);
}
