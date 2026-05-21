package com.biffis.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A user's preference for one tracker: hidden on the home grid and/or a custom
 * sort order. Plain-UUID FKs (no @ManyToOne) like the rest of the catalog, so
 * services stay query-light. Absence of a row = default (visible, default sort).
 */
@Entity
@Table(name = "user_tracker_prefs")
public class UserTrackerPref {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "event_type_id", nullable = false)
    private UUID eventTypeId;

    @Column(nullable = false)
    private boolean hidden;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected UserTrackerPref() {
    }

    public UserTrackerPref(UUID userId, UUID eventTypeId) {
        this.userId = userId;
        this.eventTypeId = eventTypeId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
