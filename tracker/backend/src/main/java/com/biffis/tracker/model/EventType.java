package com.biffis.tracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A thing you can log. Parents (is_category = true) are containers with no log
 * form; leaves carry properties. {@code parentId} / {@code createdBy} are plain
 * UUIDs so the service can build the tree from a flat ordered fetch.
 */
@Entity
@Table(name = "event_types")
public class EventType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private String icon;

    @Column(name = "color_class")
    private String colorClass;

    @Column
    private String unit;

    @Column(name = "default_value")
    private BigDecimal defaultValue;

    @Column(nullable = false)
    private String audience;

    @Column(name = "is_seed", nullable = false)
    private boolean seed;

    @Column(name = "is_category", nullable = false)
    private boolean category;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EventType() {
    }

    public EventType(UUID parentId, String slug, String name, String description, String icon,
                     String colorClass, String unit, BigDecimal defaultValue, String audience,
                     boolean category, int sortOrder, UUID createdBy) {
        this.parentId = parentId;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.colorClass = colorClass;
        this.unit = unit;
        this.defaultValue = defaultValue;
        this.audience = audience;
        this.seed = false;
        this.category = category;
        this.sortOrder = sortOrder;
        this.createdBy = createdBy;
    }

    public UUID getId() {
        return id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public String getColorClass() {
        return colorClass;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getDefaultValue() {
        return defaultValue;
    }

    public String getAudience() {
        return audience;
    }

    public boolean isSeed() {
        return seed;
    }

    public boolean isCategory() {
        return category;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
