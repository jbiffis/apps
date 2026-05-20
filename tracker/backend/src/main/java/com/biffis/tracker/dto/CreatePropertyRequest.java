package com.biffis.tracker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePropertyRequest(
        @NotBlank String name,
        String description,
        @NotBlank String presetSlug,
        boolean required,
        int sortOrder) {
}
