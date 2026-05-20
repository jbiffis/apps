package com.biffis.tracker.dto;

import java.util.UUID;

public record EventPropertyView(
        UUID id,
        String name,
        String description,
        boolean required,
        int sortOrder,
        PresetView preset) {
}
