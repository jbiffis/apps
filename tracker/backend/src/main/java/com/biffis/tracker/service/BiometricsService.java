package com.biffis.tracker.service;

import com.biffis.tracker.dto.BiometricsRequest;
import com.biffis.tracker.dto.BiometricsView;
import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.LoggedEventOptionRepository;
import com.biffis.tracker.repository.UserRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * The user's biometric profile. Stores only stable facts on the user row;
 * everything that changes (weight, height, age, BMI) is derived at read time so
 * it can never go stale. Weight/height come from the most recent logged Weight /
 * Height entry — there is no separate stored copy.
 */
@Service
public class BiometricsService {

    private static final String WEIGHT_SLUG = "weight";
    private static final String HEIGHT_SLUG = "height";

    private final UserRepository users;
    private final LoggedEventOptionRepository options;

    public BiometricsService(UserRepository users, LoggedEventOptionRepository options) {
        this.users = users;
        this.options = options;
    }

    @Transactional(readOnly = true)
    public BiometricsView get() {
        return toView(currentUser());
    }

    @Transactional
    public BiometricsView update(BiometricsRequest req) {
        User u = currentUser();
        u.setDateOfBirth(req.dateOfBirth());
        u.setBiologicalSex(blankToNull(req.biologicalSex()));
        u.setBloodType(blankToNull(req.bloodType()));
        u.setActivityLevel(blankToNull(req.activityLevel()));
        u.setWeightGoal(blankToNull(req.weightGoal()));
        u.setDrugAllergies(blankToNull(req.drugAllergies()));
        u.setChronicConditions(blankToNull(req.chronicConditions()));
        users.save(u);
        return toView(u);
    }

    private BiometricsView toView(User u) {
        UUID userId = u.getId();
        Integer age = u.getDateOfBirth() == null
                ? null
                : Period.between(u.getDateOfBirth(), LocalDate.now()).getYears();
        BigDecimal weightKg = latest(userId, WEIGHT_SLUG);
        BigDecimal heightCm = latest(userId, HEIGHT_SLUG);
        return new BiometricsView(
                u.getDateOfBirth(), u.getBiologicalSex(), u.getBloodType(),
                u.getActivityLevel(), u.getWeightGoal(), u.getDrugAllergies(),
                u.getChronicConditions(),
                age, weightKg, heightCm, bmi(weightKg, heightCm));
    }

    private User currentUser() {
        return users.findById(CurrentUser.id())
                .orElseThrow(() -> new NotFoundException("user not found"));
    }

    /** Most recent logged measurement for a single-measurement tracker, or null. */
    private BigDecimal latest(UUID userId, String slug) {
        return options.findLatestNumericValue(userId, slug)
                .map(BiometricsService::parseDecimal)
                .orElse(null);
    }

    private static BigDecimal parseDecimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null; // a non-numeric value should never reach here, but never 500 on it
        }
    }

    /** BMI = kg / m². One decimal. Null unless both measurements exist. */
    private static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.movePointLeft(2);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
