package com.biffis.tracker.dto;

import java.util.List;

/**
 * Activity stats for the Stats tab, over a trailing window. Day buckets are
 * in the caller's {@code tz} (the {@code /api/stats?tz=…} query param,
 * defaulting to UTC) so the boundaries line up with the user's local midnight.
 * See LoggedEventRepository.countByDay.
 */
public record StatsResponse(
        String from,
        String to,
        long totalEntries,
        int currentStreakDays,
        int longestStreakDays,
        List<TrackerCount> perTracker,
        List<DayCount> daily) {

    public record TrackerCount(String eventTypeSlug, String name, String icon, long count) {
    }

    public record DayCount(String date, long count) {
    }
}
