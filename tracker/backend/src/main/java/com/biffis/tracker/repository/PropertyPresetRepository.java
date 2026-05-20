package com.biffis.tracker.repository;

import com.biffis.tracker.model.PropertyPreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PropertyPresetRepository extends JpaRepository<PropertyPreset, UUID> {

    Optional<PropertyPreset> findBySlug(String slug);
}
