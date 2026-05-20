package com.biffis.tracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public record LogEventRequest(
        @NotBlank String eventTypeSlug,
        OffsetDateTime occurredAt,
        String note,
        @Valid List<LogOptionRequest> options) {
}
