package com.biffis.tracker.service;

import com.biffis.tracker.dto.CreateEventTypeRequest;
import com.biffis.tracker.dto.CreatePropertyRequest;
import com.biffis.tracker.dto.EventPropertyView;
import com.biffis.tracker.dto.EventTypeView;
import com.biffis.tracker.dto.PresetView;
import com.biffis.tracker.exception.ConflictException;
import com.biffis.tracker.exception.ForbiddenException;
import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.EventProperty;
import com.biffis.tracker.model.EventType;
import com.biffis.tracker.model.PropertyPreset;
import com.biffis.tracker.repository.EventPropertyRepository;
import com.biffis.tracker.repository.EventTypeRepository;
import com.biffis.tracker.repository.PropertyPresetRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read + write the shared catalog (event_types / event_properties /
 * property_presets). Catalog data is shared across all users; only logged
 * events are per-user (those live in a later epic). Audience filtering here is
 * UI-visibility, not access control — anyone can bypass it with includeAll.
 */
@Service
public class CatalogService {

    private final EventTypeRepository eventTypes;
    private final EventPropertyRepository properties;
    private final PropertyPresetRepository presets;
    private final UserService userService;

    public CatalogService(EventTypeRepository eventTypes, EventPropertyRepository properties,
                          PropertyPresetRepository presets, UserService userService) {
        this.eventTypes = eventTypes;
        this.properties = properties;
        this.presets = presets;
        this.userService = userService;
    }

    // ---------- reads ----------

    /**
     * Full catalog as a tree. When includeAll is false, nodes whose audience
     * doesn't match the caller's gender are dropped (a male caller doesn't see
     * female-audience trackers by default, and vice versa).
     */
    @Transactional(readOnly = true)
    public List<EventTypeView> tree(boolean includeAll) {
        String gender = includeAll ? null : userService.currentGender();

        Map<UUID, PropertyPreset> presetById = presets.findAll().stream()
                .collect(Collectors.toMap(PropertyPreset::getId, Function.identity()));
        Map<UUID, List<EventProperty>> propsByType = properties.findAll().stream()
                .collect(Collectors.groupingBy(EventProperty::getEventTypeId));

        // Build view nodes (audience-filtered), then wire parent/child.
        Map<UUID, MutableNode> nodes = new java.util.LinkedHashMap<>();
        for (EventType t : eventTypes.findAllByOrderBySortOrderAscNameAsc()) {
            if (!visible(t.getAudience(), gender)) {
                continue;
            }
            nodes.put(t.getId(), new MutableNode(t, propertyViews(propsByType.get(t.getId()), presetById)));
        }

        // Wire the whole tree first, THEN convert to immutable views. toView()
        // snapshots children recursively, so converting a root before all its
        // descendants are linked silently truncates the tree — node iteration
        // order interleaves parents and children by sort order.
        List<MutableNode> roots = new ArrayList<>();
        for (MutableNode node : nodes.values()) {
            UUID parentId = node.type.getParentId();
            if (parentId != null && nodes.containsKey(parentId)) {
                nodes.get(parentId).children.add(node);
            } else {
                roots.add(node);
            }
        }
        return roots.stream().map(MutableNode::toView).toList();
    }

    @Transactional(readOnly = true)
    public EventTypeView bySlug(String slug) {
        EventType t = eventTypes.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("event type not found"));
        Map<UUID, PropertyPreset> presetById = presets.findAll().stream()
                .collect(Collectors.toMap(PropertyPreset::getId, Function.identity()));
        List<EventPropertyView> props = propertyViews(
                properties.findByEventTypeIdOrderBySortOrderAsc(t.getId()), presetById);
        return toLeafView(t, props);
    }

    @Transactional(readOnly = true)
    public List<PresetView> allPresets() {
        return presets.findAll().stream()
                .sorted((a, b) -> a.getSlug().compareTo(b.getSlug()))
                .map(PresetView::of)
                .toList();
    }

    // ---------- writes ----------

    /** Create a new (non-seed) event type, shared with all users. */
    @Transactional
    public EventTypeView create(CreateEventTypeRequest req) {
        UUID parentId = null;
        if (req.parentSlug() != null && !req.parentSlug().isBlank()) {
            parentId = eventTypes.findBySlug(req.parentSlug())
                    .orElseThrow(() -> new NotFoundException("parent not found"))
                    .getId();
        }

        String slug = slugify(req.name());
        if (eventTypes.existsBySlug(slug)) {
            throw new ConflictException("slug already exists");
        }

        String audience = (req.audience() == null || req.audience().isBlank()) ? "all" : req.audience();
        EventType created = eventTypes.save(new EventType(
                parentId, slug, req.name(), null, req.icon(), req.colorClass(),
                req.unit(), req.defaultValue(), audience, req.isCategory(), 0, CurrentUser.id()));

        if (req.properties() != null) {
            for (CreatePropertyRequest p : req.properties()) {
                PropertyPreset preset = presets.findBySlug(p.presetSlug())
                        .orElseThrow(() -> new NotFoundException("preset not found"));
                properties.save(new EventProperty(created.getId(), p.name(), p.description(),
                        preset.getId(), p.required(), p.sortOrder()));
            }
        }

        return bySlug(slug);
    }

    /** Delete an event type. Only the creator may delete, and only non-seed entries. */
    @Transactional
    public void delete(UUID id) {
        EventType t = eventTypes.findById(id)
                .orElseThrow(() -> new NotFoundException("event type not found"));
        if (t.isSeed()) {
            throw new ForbiddenException("cannot delete a seed event type");
        }
        if (t.getCreatedBy() == null || !t.getCreatedBy().equals(CurrentUser.id())) {
            throw new ForbiddenException("only the creator may delete this event type");
        }
        eventTypes.delete(t); // event_properties cascade via FK ON DELETE CASCADE
    }

    // ---------- helpers ----------

    private static boolean visible(String audience, String gender) {
        if (gender == null) {
            return true; // includeAll, or user with no gender set sees everything
        }
        return "all".equals(audience) || audience.equals(gender);
    }

    private List<EventPropertyView> propertyViews(List<EventProperty> list,
                                                  Map<UUID, PropertyPreset> presetById) {
        if (list == null) {
            return List.of();
        }
        return list.stream()
                .sorted((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .map(p -> {
                    PropertyPreset preset = presetById.get(p.getPresetId());
                    return new EventPropertyView(p.getId(), p.getName(), p.getDescription(),
                            p.isRequired(), p.getSortOrder(),
                            preset == null ? null : PresetView.of(preset));
                })
                .toList();
    }

    private EventTypeView toLeafView(EventType t, List<EventPropertyView> props) {
        return new EventTypeView(t.getId(), t.getParentId(), t.getSlug(), t.getName(),
                t.getDescription(), t.getIcon(), t.getColorClass(), t.getUnit(), t.getDefaultValue(),
                t.getAudience(), t.isCategory(), t.isSeed(), t.getSortOrder(), List.of(), props);
    }

    private static String slugify(String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return n.isBlank() ? UUID.randomUUID().toString() : n;
    }

    /** Mutable assembly node — converted to the immutable record once the tree is wired. */
    private final class MutableNode {
        final EventType type;
        final List<EventPropertyView> props;
        final List<MutableNode> children = new ArrayList<>();

        MutableNode(EventType type, List<EventPropertyView> props) {
            this.type = type;
            this.props = props;
        }

        EventTypeView toView() {
            List<EventTypeView> childViews = children.stream().map(MutableNode::toView).toList();
            return new EventTypeView(type.getId(), type.getParentId(), type.getSlug(), type.getName(),
                    type.getDescription(), type.getIcon(), type.getColorClass(), type.getUnit(),
                    type.getDefaultValue(), type.getAudience(), type.isCategory(), type.isSeed(),
                    type.getSortOrder(), childViews, props);
        }
    }
}
