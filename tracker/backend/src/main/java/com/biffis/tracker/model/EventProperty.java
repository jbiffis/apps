package com.biffis.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A field shown on an event type's entry screen. {@code presetId} /
 * {@code eventTypeId} are kept as plain UUIDs (not @ManyToOne) so the catalog
 * service can stitch the tree from a few bulk queries without lazy-loading
 * (open-in-view is off).
 */
@Entity
@Table(name = "event_properties")
public class EventProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type_id", nullable = false)
    private UUID eventTypeId;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(name = "preset_id", nullable = false)
    private UUID presetId;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected EventProperty() {
    }

    public EventProperty(UUID eventTypeId, String name, String description,
                         UUID presetId, boolean required, int sortOrder) {
        this.eventTypeId = eventTypeId;
        this.name = name;
        this.description = description;
        this.presetId = presetId;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventTypeId() {
        return eventTypeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getPresetId() {
        return presetId;
    }

    public boolean isRequired() {
        return required;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
