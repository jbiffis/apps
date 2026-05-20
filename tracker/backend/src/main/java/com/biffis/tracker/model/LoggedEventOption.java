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

/** A property value on a logged entry. {@code value} jsonb: number/string/array. */
@Entity
@Table(name = "logged_event_options")
public class LoggedEventOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "logged_event_id", nullable = false)
    private UUID loggedEventId;

    @Column(name = "event_property_id", nullable = false)
    private UUID eventPropertyId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode value;

    protected LoggedEventOption() {
    }

    public LoggedEventOption(UUID loggedEventId, UUID eventPropertyId, JsonNode value) {
        this.loggedEventId = loggedEventId;
        this.eventPropertyId = eventPropertyId;
        this.value = value;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLoggedEventId() {
        return loggedEventId;
    }

    public UUID getEventPropertyId() {
        return eventPropertyId;
    }

    public JsonNode getValue() {
        return value;
    }
}
