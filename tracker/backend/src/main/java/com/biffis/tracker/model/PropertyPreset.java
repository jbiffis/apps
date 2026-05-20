package com.biffis.tracker.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/** Reusable widget definition (see V1__schema.sql / V2 seed). */
@Entity
@Table(name = "property_presets")
public class PropertyPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String widget;

    /** jsonb; shape depends on widget (see DATA_MODEL.md). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode options;

    @Column(name = "is_seed", nullable = false)
    private boolean seed;

    protected PropertyPreset() {
    }

    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getWidget() {
        return widget;
    }

    public JsonNode getOptions() {
        return options;
    }

    public boolean isSeed() {
        return seed;
    }
}
