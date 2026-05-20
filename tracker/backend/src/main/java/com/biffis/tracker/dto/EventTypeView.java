package com.biffis.tracker.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Catalog node. Categories carry {@code children}; leaves carry
 * {@code properties}. Both lists are always present (possibly empty) so the
 * frontend doesn't have to null-check.
 */
public record EventTypeView(
        UUID id,
        UUID parentId,
        String slug,
        String name,
        String description,
        String icon,
        String colorClass,
        String unit,
        BigDecimal defaultValue,
        String audience,
        boolean isCategory,
        boolean isSeed,
        int sortOrder,
        List<EventTypeView> children,
        List<EventPropertyView> properties) {
}
