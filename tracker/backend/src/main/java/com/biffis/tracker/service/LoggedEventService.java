package com.biffis.tracker.service;

import com.biffis.tracker.dto.LogEventRequest;
import com.biffis.tracker.dto.LogOptionRequest;
import com.biffis.tracker.dto.LoggedEventView;
import com.biffis.tracker.dto.LoggedEventsResponse;
import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.EventProperty;
import com.biffis.tracker.model.EventType;
import com.biffis.tracker.model.LoggedEvent;
import com.biffis.tracker.model.LoggedEventOption;
import com.biffis.tracker.repository.EventPropertyRepository;
import com.biffis.tracker.repository.EventTypeRepository;
import com.biffis.tracker.repository.LoggedEventOptionRepository;
import com.biffis.tracker.repository.LoggedEventRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Logged events — the only per-user data in the app. EVERY method derives the
 * owner from {@code CurrentUser.id()} and scopes all reads/writes/deletes to
 * it. There is no cross-user read path: another user's id returns "not found",
 * not "forbidden" (so we don't confirm an id exists).
 */
@Service
public class LoggedEventService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final LoggedEventRepository events;
    private final LoggedEventOptionRepository options;
    private final EventTypeRepository eventTypes;
    private final EventPropertyRepository properties;

    public LoggedEventService(LoggedEventRepository events, LoggedEventOptionRepository options,
                              EventTypeRepository eventTypes, EventPropertyRepository properties) {
        this.events = events;
        this.options = options;
        this.eventTypes = eventTypes;
        this.properties = properties;
    }

    // ---------- write ----------

    @Transactional
    public LoggedEventView create(LogEventRequest req) {
        UUID userId = CurrentUser.id();
        EventType type = eventTypes.findBySlug(req.eventTypeSlug())
                .orElseThrow(() -> new NotFoundException("event type not found"));

        OffsetDateTime when = req.occurredAt() != null ? req.occurredAt() : OffsetDateTime.now();
        LoggedEvent saved = events.save(new LoggedEvent(userId, type.getId(), when, req.note()));

        if (req.options() != null) {
            for (LogOptionRequest opt : req.options()) {
                EventProperty prop = resolveProperty(type.getId(), opt);
                options.save(new LoggedEventOption(saved.getId(), prop.getId(), opt.value()));
            }
        }
        return hydrate(List.of(saved)).get(0);
    }

    /**
     * Replace an existing entry the caller owns. occurredAt/note are overwritten
     * and the option set is fully replaced. Another user's id → not found.
     */
    @Transactional
    public LoggedEventView update(UUID id, LogEventRequest req) {
        UUID userId = CurrentUser.id();
        LoggedEvent existing = events.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("logged event not found"));
        EventType type = eventTypes.findBySlug(req.eventTypeSlug())
                .orElseThrow(() -> new NotFoundException("event type not found"));

        existing.setEventTypeId(type.getId());
        existing.setOccurredAt(req.occurredAt() != null ? req.occurredAt() : existing.getOccurredAt());
        existing.setNote(req.note());
        events.save(existing);

        // Flush the deletes before re-inserting: Hibernate orders inserts ahead
        // of deletes within a flush, which would otherwise trip the
        // (logged_event_id, event_property_id) unique constraint.
        options.deleteByLoggedEventId(existing.getId());
        options.flush();
        if (req.options() != null) {
            for (LogOptionRequest opt : req.options()) {
                EventProperty prop = resolveProperty(type.getId(), opt);
                options.save(new LoggedEventOption(existing.getId(), prop.getId(), opt.value()));
            }
        }
        return hydrate(List.of(existing)).get(0);
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public LoggedEventsResponse list(OffsetDateTime from, OffsetDateTime to, String eventTypeSlug,
                                     Integer limit, String cursor) {
        UUID userId = CurrentUser.id();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime toBound = to != null ? to : now;
        OffsetDateTime fromBound = from != null ? from : now.minusDays(1);
        int cap = clampLimit(limit);

        UUID eventTypeId = null;
        if (eventTypeSlug != null && !eventTypeSlug.isBlank()) {
            eventTypeId = eventTypes.findBySlug(eventTypeSlug)
                    .orElseThrow(() -> new NotFoundException("event type not found"))
                    .getId();
        }

        OffsetDateTime curTs = null;
        UUID curId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int sep = decoded.lastIndexOf('_');
                curTs = OffsetDateTime.parse(decoded.substring(0, sep));
                curId = UUID.fromString(decoded.substring(sep + 1));
            } catch (RuntimeException e) {
                throw new NotFoundException("invalid cursor");
            }
        }

        List<LoggedEvent> rows = events.findScoped(userId, fromBound, toBound, eventTypeId,
                curTs == null, curTs, curId, PageRequest.of(0, cap));
        // Full page → there may be more; hand back a keyset cursor on the last row.
        String next = null;
        if (rows.size() == cap && !rows.isEmpty()) {
            LoggedEvent last = rows.get(rows.size() - 1);
            String raw = last.getOccurredAt().toString() + "_" + last.getId();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }
        return new LoggedEventsResponse(hydrate(rows), next);
    }

    @Transactional(readOnly = true)
    public LoggedEventView getOne(UUID id) {
        LoggedEvent e = events.findByIdAndUserId(id, CurrentUser.id())
                .orElseThrow(() -> new NotFoundException("logged event not found"));
        return hydrate(List.of(e)).get(0);
    }

    /** Today's entries for the home feed, oldest → newest, capped. */
    @Transactional(readOnly = true)
    public List<LoggedEventView> today(int max) {
        UUID userId = CurrentUser.id();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay(now.getOffset()).toOffsetDateTime();
        List<LoggedEvent> rows = events.findScoped(userId, startOfDay, now, null, true, null, null, PageRequest.of(0, max));
        List<LoggedEventView> views = new ArrayList<>(hydrate(rows));
        views.sort(Comparator.comparing(LoggedEventView::occurredAt)); // oldest first
        return views;
    }

    // ---------- delete ----------

    @Transactional
    public void delete(UUID id) {
        long removed = events.deleteByIdAndUserId(id, CurrentUser.id());
        if (removed == 0) {
            throw new NotFoundException("logged event not found");
        }
        // logged_event_options cascade via FK ON DELETE CASCADE
    }

    // ---------- helpers ----------

    private EventProperty resolveProperty(UUID eventTypeId, LogOptionRequest opt) {
        EventProperty prop;
        if (opt.propertyId() != null) {
            prop = properties.findById(opt.propertyId())
                    .orElseThrow(() -> new NotFoundException("property not found"));
        } else if (opt.propertyName() != null && !opt.propertyName().isBlank()) {
            prop = properties.findByEventTypeIdAndName(eventTypeId, opt.propertyName())
                    .orElseThrow(() -> new NotFoundException("property not found"));
        } else {
            throw new NotFoundException("option needs propertyId or propertyName");
        }
        // Property must belong to the event type being logged.
        if (!prop.getEventTypeId().equals(eventTypeId)) {
            throw new NotFoundException("property does not belong to this event type");
        }
        return prop;
    }

    /** Batch-build views for a set of (already user-scoped) events. */
    private List<LoggedEventView> hydrate(List<LoggedEvent> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> typeIds = rows.stream().map(LoggedEvent::getEventTypeId).distinct().toList();
        Map<UUID, EventType> typeById = eventTypes.findAllById(typeIds).stream()
                .collect(Collectors.toMap(EventType::getId, Function.identity()));

        List<UUID> eventIds = rows.stream().map(LoggedEvent::getId).toList();
        List<LoggedEventOption> allOptions = options.findByLoggedEventIdIn(eventIds);
        Map<UUID, List<LoggedEventOption>> optsByEvent = allOptions.stream()
                .collect(Collectors.groupingBy(LoggedEventOption::getLoggedEventId));

        List<UUID> propIds = allOptions.stream().map(LoggedEventOption::getEventPropertyId).distinct().toList();
        Map<UUID, EventProperty> propById = propIds.isEmpty() ? Map.of()
                : properties.findAllById(propIds).stream()
                        .collect(Collectors.toMap(EventProperty::getId, Function.identity()));

        return rows.stream().map(e -> {
            EventType t = typeById.get(e.getEventTypeId());
            var ref = t == null ? null : new LoggedEventView.EventTypeRef(t.getSlug(), t.getName());
            List<LoggedEventView.OptionView> optViews = optsByEvent.getOrDefault(e.getId(), List.of())
                    .stream()
                    .map(o -> {
                        EventProperty p = propById.get(o.getEventPropertyId());
                        return new LoggedEventView.OptionView(p == null ? null : p.getName(), o.getValue());
                    })
                    .toList();
            return new LoggedEventView(e.getId(), ref, e.getOccurredAt(), e.getNote(), optViews, e.getCreatedAt());
        }).toList();
    }

    private int clampLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
