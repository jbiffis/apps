package com.biffis.tracker.dto;

import com.biffis.tracker.model.PropertyPreset;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

public record PresetView(UUID id, String slug, String name, String widget, JsonNode options) {

    public static PresetView of(PropertyPreset p) {
        return new PresetView(p.getId(), p.getSlug(), p.getName(), p.getWidget(), p.getOptions());
    }
}
