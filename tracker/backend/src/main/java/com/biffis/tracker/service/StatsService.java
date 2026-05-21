package com.biffis.tracker.service;

import com.biffis.tracker.dto.StatsResponse;
import com.biffis.tracker.model.EventType;
import com.biffis.tracker.repository.EventTypeRepository;
import com.biffis.tracker.repository.LoggedEventRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Activity aggregates for the Stats tab. Always scoped to {@link CurrentUser}. */
@Service
public class StatsService {

    private static final int MAX_DAYS = 366;

    private final LoggedEventRepository events;
    private final EventTypeRepository eventTypes;

    public StatsService(LoggedEventRepository events, EventTypeRepository eventTypes) {
        this.events = events;
        this.eventTypes = eventTypes;
    }

    @Transactional(readOnly = true)
    public StatsResponse summary(Integer days) {
        UUID userId = CurrentUser.id();
        int window = days == null ? 84 : Math.max(1, Math.min(days, MAX_DAYS));
        OffsetDateTime to = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime from = to.minusDays(window);

        // Per-tracker totals → hydrate to slug/name/icon, sorted desc.
        List<Object[]> typeRows = events.countByType(userId, from, to);
        Map<UUID, Long> countByTypeId = typeRows.stream().collect(Collectors.toMap(
                r -> UUID.fromString((String) r[0]),
                r -> ((Number) r[1]).longValue()));
        Map<UUID, EventType> typeById = eventTypes.findAllById(countByTypeId.keySet()).stream()
                .collect(Collectors.toMap(EventType::getId, Function.identity()));
        List<StatsResponse.TrackerCount> perTracker = countByTypeId.entrySet().stream()
                .map(e -> {
                    EventType t = typeById.get(e.getKey());
                    return new StatsResponse.TrackerCount(
                            t == null ? null : t.getSlug(),
                            t == null ? "(unknown)" : t.getName(),
                            t == null ? null : t.getIcon(),
                            e.getValue());
                })
                .sorted(Comparator.comparingLong(StatsResponse.TrackerCount::count).reversed())
                .toList();

        // Daily series for the heatmap.
        List<Object[]> dayRows = events.countByDay(userId, from, to);
        List<StatsResponse.DayCount> daily = dayRows.stream()
                .map(r -> new StatsResponse.DayCount((String) r[0], ((Number) r[1]).longValue()))
                .toList();

        long total = daily.stream().mapToLong(StatsResponse.DayCount::count).sum();
        Set<LocalDate> activeDays = daily.stream().map(d -> LocalDate.parse(d.date())).collect(Collectors.toCollection(HashSet::new));

        return new StatsResponse(
                from.toString(), to.toString(), total,
                currentStreak(activeDays), longestStreak(activeDays),
                perTracker, daily);
    }

    /** Consecutive days with activity, ending today (or yesterday if nothing logged yet today). */
    private int currentStreak(Set<LocalDate> active) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate cur = active.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (active.contains(cur)) {
            streak++;
            cur = cur.minusDays(1);
        }
        return streak;
    }

    private int longestStreak(Set<LocalDate> active) {
        int best = 0;
        for (LocalDate d : active) {
            if (active.contains(d.minusDays(1))) {
                continue; // not a run start
            }
            int len = 0;
            LocalDate cur = d;
            while (active.contains(cur)) {
                len++;
                cur = cur.plusDays(1);
            }
            best = Math.max(best, len);
        }
        return best;
    }
}
