package com.biffis.tracker.repository;

import com.biffis.tracker.model.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

    Optional<EventType> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<EventType> findAllByOrderBySortOrderAscNameAsc();
}
