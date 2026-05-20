package com.biffis.tracker.repository;

import com.biffis.tracker.model.EventProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventPropertyRepository extends JpaRepository<EventProperty, UUID> {

    List<EventProperty> findByEventTypeIdOrderBySortOrderAsc(UUID eventTypeId);

    Optional<EventProperty> findByEventTypeIdAndName(UUID eventTypeId, String name);
}
