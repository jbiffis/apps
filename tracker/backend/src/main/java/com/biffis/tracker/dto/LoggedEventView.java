package com.biffis.tracker.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record LoggedEventView(
        UUID id,
        EventTypeRef eventType,
        OffsetDateTime occurredAt,
        String note,
        List<OptionView> options,
        OffsetDateTime createdAt) {

    public record EventTypeRef(String slug, String name) {
    }

    public record OptionView(String property, JsonNode value) {
    }
}
