package com.biffis.tracker.dto;

import java.util.List;

/**
 * Activity stats for the Stats tab, over a trailing window. Day buckets are
 * UTC dates (see LoggedEventRepository.countByDay).
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
