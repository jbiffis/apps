package com.biffis.tracker.dto;

public record HeroCard(
        String eventTypeSlug,
        String name,
        String icon,
        boolean primary,
        String valueText,
        String subText,
        String captionText,
        double progress) {
}
