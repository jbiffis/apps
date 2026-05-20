package com.biffis.tracker.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/**
 * One property value when logging an event. Identify the property by
 * {@code propertyId} OR {@code propertyName} (resolved server-side against the
 * event type). {@code value} is free-form jsonb (number / string / array).
 */
public record LogOptionRequest(UUID propertyId, String propertyName, JsonNode value) {
}
