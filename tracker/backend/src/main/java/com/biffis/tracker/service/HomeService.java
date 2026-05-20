package com.biffis.tracker.service;

import com.biffis.tracker.dto.HeroCard;
import com.biffis.tracker.model.EventType;
import com.biffis.tracker.repository.EventTypeRepository;
import com.biffis.tracker.repository.LoggedEventRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Home-screen aggregates. Phase 1 hero is a fixed set (medication, water,
 * sleep) with hardcoded daily targets — there's no goals schema yet (Phase 2).
 * Counts are today's logged events for the tracker (a category sums its leaf
 * descendants). All counts are user-scoped via LoggedEventRepository.
 */
@Service
public class HomeService {

    private record HeroSpec(String slug, int target, String unitCaption, boolean primary) {
    }

    private static final List<HeroSpec> HERO = List.of(
            new HeroSpec("medication", 3, "doses", true),
            new HeroSpec("water", 8, "glasses", false),
            new HeroSpec("sleep", 1, "logged", false));

    private final EventTypeRepository eventTypes;
    private final LoggedEventRepository events;

    public HomeService(EventTypeRepository eventTypes, LoggedEventRepository events) {
        this.eventTypes = eventTypes;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<HeroCard> hero() {
        UUID userId = CurrentUser.id();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();

        List<EventType> all = eventTypes.findAllByOrderBySortOrderAscNameAsc();
        Map<UUID, EventType> byId = all.stream().collect(Collectors.toMap(EventType::getId, t -> t));
        Map<UUID, List<EventType>> childrenByParent = all.stream()
                .filter(t -> t.getParentId() != null)
                .collect(Collectors.groupingBy(EventType::getParentId));

        List<HeroCard> cards = new ArrayList<>();
        for (HeroSpec spec : HERO) {
            EventType node = all.stream().filter(t -> spec.slug.equals(t.getSlug())).findFirst().orElse(null);
            if (node == null) {
                // tracker not in catalog — emit an empty card so the UI shape is stable
                cards.add(new HeroCard(spec.slug, spec.slug, "Habit", spec.primary, "0",
                        "/" + spec.target, spec.unitCaption, 0.0));
                continue;
            }
            List<UUID> typeIds = countableIds(node, childrenByParent);
            long count = typeIds.isEmpty() ? 0
                    : events.countScopedForTypes(userId, typeIds, startOfDay, now);
            double progress = spec.target <= 0 ? 0.0 : Math.min(1.0, (double) count / spec.target);
            String caption = spec.target > 1
                    ? Math.max(0, spec.target - count) + " left"
                    : spec.unitCaption;
            cards.add(new HeroCard(node.getSlug(), node.getName(), node.getIcon(), spec.primary,
                    String.valueOf(count), "/" + spec.target, caption, progress));
        }
        return cards;
    }

    /** A leaf counts itself; a category counts all leaf descendants. */
    private List<UUID> countableIds(EventType node, Map<UUID, List<EventType>> childrenByParent) {
        if (!node.isCategory()) {
            return List.of(node.getId());
        }
        List<UUID> ids = new ArrayList<>();
        Deque<EventType> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            EventType cur = stack.pop();
            for (EventType child : childrenByParent.getOrDefault(cur.getId(), List.of())) {
                if (child.isCategory()) {
                    stack.push(child);
                } else {
                    ids.add(child.getId());
                }
            }
        }
        return ids;
    }
}
