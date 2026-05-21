package com.biffis.tracker.dto;

/** Partial update for a tracker preference. Null fields are left unchanged. */
public record TrackerPrefRequest(Boolean hidden, Integer sortOrder) {
}
