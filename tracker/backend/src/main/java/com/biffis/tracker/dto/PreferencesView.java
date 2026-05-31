package com.biffis.tracker.dto;

/**
 * The user's measurement unit preferences. Presentation only — logged values
 * are always stored in canonical metric; these tell the frontend how to display
 * and enter them.
 *
 * <ul>
 *   <li>{@code weightUnit}: {@code kg} (canonical) | {@code lb}</li>
 *   <li>{@code heightUnit}: {@code cm} (canonical) | {@code ftin}</li>
 *   <li>{@code temperatureUnit}: {@code c} (canonical) | {@code f}</li>
 * </ul>
 */
public record PreferencesView(
        String weightUnit,
        String heightUnit,
        String temperatureUnit) {
}
