package com.biffis.tracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The user's biometric profile. {@code stored} fields are stable facts kept on
 * the user row; {@code derived} fields are computed at read time so they never
 * go stale:
 *
 * <ul>
 *   <li>{@code age} from {@code dateOfBirth}</li>
 *   <li>{@code latestWeightKg} / {@code latestHeightCm} from the most recent
 *       logged Weight / Height entry</li>
 *   <li>{@code bmi} from the two latest measurements</li>
 * </ul>
 */
public record BiometricsView(
        // stored
        LocalDate dateOfBirth,
        String biologicalSex,
        String bloodType,
        String activityLevel,
        String weightGoal,
        String drugAllergies,
        String chronicConditions,
        // derived
        Integer age,
        BigDecimal latestWeightKg,
        BigDecimal latestHeightCm,
        BigDecimal bmi) {
}
