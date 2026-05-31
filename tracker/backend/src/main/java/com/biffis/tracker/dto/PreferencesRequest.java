package com.biffis.tracker.dto;

import jakarta.validation.constraints.Pattern;

/**
 * Update measurement unit preferences. Each field is optional — a null field is
 * left unchanged (partial update). Non-null values are validated against the
 * same vocabularies as the V9 CHECK constraints.
 */
public record PreferencesRequest(
        @Pattern(regexp = "^(kg|lb)$", message = "invalid weight unit")
        String weightUnit,

        @Pattern(regexp = "^(cm|ftin)$", message = "invalid height unit")
        String heightUnit,

        @Pattern(regexp = "^(c|f)$", message = "invalid temperature unit")
        String temperatureUnit) {
}
