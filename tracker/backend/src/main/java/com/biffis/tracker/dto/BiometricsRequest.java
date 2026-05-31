package com.biffis.tracker.dto;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Update the stored biometric facts. Weight/height are NOT here — those are
 * logged as events. Every field is optional; null (or "") clears it. Enum-like
 * fields are validated against the same vocabularies as the V8 CHECK
 * constraints, with "" allowed so the UI can submit an unset select.
 */
public record BiometricsRequest(
        @PastOrPresent(message = "date of birth cannot be in the future")
        LocalDate dateOfBirth,

        @Pattern(regexp = "^(male|female|intersex)?$", message = "invalid biological sex")
        String biologicalSex,

        @Pattern(regexp = "^(A\\+|A-|B\\+|B-|AB\\+|AB-|O\\+|O-)?$", message = "invalid blood type")
        String bloodType,

        @Pattern(regexp = "^(sedentary|light|moderate|active|very_active)?$", message = "invalid activity level")
        String activityLevel,

        @Pattern(regexp = "^(lose|maintain|gain)?$", message = "invalid weight goal")
        String weightGoal,

        @Size(max = 2000, message = "drug allergies too long")
        String drugAllergies,

        @Size(max = 2000, message = "chronic conditions too long")
        String chronicConditions) {
}
