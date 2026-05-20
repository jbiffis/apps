package com.biffis.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.util.List;

/**
 * Create a new (non-seed) event type. {@code parentSlug} optional — null means
 * a top-level type. Properties optional.
 */
public record CreateEventTypeRequest(
        String parentSlug,
        @NotBlank String name,
        @NotBlank String icon,
        String colorClass,
        String audience,
        String unit,
        BigDecimal defaultValue,
        boolean isCategory,
        @Valid List<CreatePropertyRequest> properties) {
}
