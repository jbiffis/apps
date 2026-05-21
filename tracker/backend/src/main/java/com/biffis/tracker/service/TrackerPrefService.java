package com.biffis.tracker.service;

import com.biffis.tracker.dto.TrackerPrefRequest;
import com.biffis.tracker.dto.TrackerPrefView;
import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.EventType;
import com.biffis.tracker.model.UserTrackerPref;
import com.biffis.tracker.repository.EventTypeRepository;
import com.biffis.tracker.repository.UserTrackerPrefRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Per-user tracker preferences (hide/show + custom order). Always scoped to
 * {@link CurrentUser}. Catalog itself is shared; only these prefs are per-user.
 */
@Service
public class TrackerPrefService {

    private final UserTrackerPrefRepository prefs;
    private final EventTypeRepository eventTypes;

    public TrackerPrefService(UserTrackerPrefRepository prefs, EventTypeRepository eventTypes) {
        this.prefs = prefs;
        this.eventTypes = eventTypes;
    }

    @Transactional(readOnly = true)
    public List<TrackerPrefView> list() {
        List<UserTrackerPref> rows = prefs.findByUserId(CurrentUser.id());
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> slugById = eventTypes.findAllById(
                        rows.stream().map(UserTrackerPref::getEventTypeId).toList()).stream()
                .collect(Collectors.toMap(EventType::getId, EventType::getSlug));
        return rows.stream()
                .filter(p -> slugById.containsKey(p.getEventTypeId()))
                .map(p -> new TrackerPrefView(slugById.get(p.getEventTypeId()), p.isHidden(), p.getSortOrder()))
                .toList();
    }

    /** Upsert the caller's pref for one tracker; null request fields are left unchanged. */
    @Transactional
    public TrackerPrefView upsert(String slug, TrackerPrefRequest req) {
        UUID userId = CurrentUser.id();
        EventType type = eventTypes.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("event type not found"));
        UserTrackerPref pref = prefs.findByUserIdAndEventTypeId(userId, type.getId())
                .orElseGet(() -> new UserTrackerPref(userId, type.getId()));
        if (req.hidden() != null) {
            pref.setHidden(req.hidden());
        }
        if (req.sortOrder() != null) {
            pref.setSortOrder(req.sortOrder());
        }
        prefs.save(pref);
        return new TrackerPrefView(slug, pref.isHidden(), pref.getSortOrder());
    }
}
