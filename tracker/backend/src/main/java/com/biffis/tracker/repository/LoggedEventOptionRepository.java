package com.biffis.tracker.repository;

import com.biffis.tracker.model.LoggedEventOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LoggedEventOptionRepository extends JpaRepository<LoggedEventOption, UUID> {

    List<LoggedEventOption> findByLoggedEventIdIn(Collection<UUID> loggedEventIds);
}
