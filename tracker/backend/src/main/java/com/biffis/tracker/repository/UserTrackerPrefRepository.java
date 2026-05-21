package com.biffis.tracker.repository;

import com.biffis.tracker.model.UserTrackerPref;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTrackerPrefRepository extends JpaRepository<UserTrackerPref, UUID> {

    List<UserTrackerPref> findByUserId(UUID userId);

    Optional<UserTrackerPref> findByUserIdAndEventTypeId(UUID userId, UUID eventTypeId);
}
