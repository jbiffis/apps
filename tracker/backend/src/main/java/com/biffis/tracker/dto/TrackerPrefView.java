package com.biffis.tracker.dto;

public record TrackerPrefView(String eventTypeSlug, boolean hidden, Integer sortOrder) {
}
