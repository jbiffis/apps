package com.biffis.tracker.controller;

import com.biffis.tracker.dto.BiometricsRequest;
import com.biffis.tracker.dto.BiometricsView;
import com.biffis.tracker.dto.PreferencesRequest;
import com.biffis.tracker.dto.PreferencesView;
import com.biffis.tracker.dto.TrackerPrefRequest;
import com.biffis.tracker.dto.TrackerPrefView;
import com.biffis.tracker.service.BiometricsService;
import com.biffis.tracker.service.PreferencesService;
import com.biffis.tracker.service.TrackerPrefService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Current-user settings used by the Me tab. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final TrackerPrefService prefs;
    private final BiometricsService biometrics;
    private final PreferencesService preferences;

    public MeController(TrackerPrefService prefs, BiometricsService biometrics,
                        PreferencesService preferences) {
        this.prefs = prefs;
        this.biometrics = biometrics;
        this.preferences = preferences;
    }

    @GetMapping("/tracker-prefs")
    public List<TrackerPrefView> trackerPrefs() {
        return prefs.list();
    }

    @PutMapping("/tracker-prefs/{slug}")
    public TrackerPrefView setTrackerPref(@PathVariable String slug, @RequestBody TrackerPrefRequest req) {
        return prefs.upsert(slug, req);
    }

    @GetMapping("/biometrics")
    public BiometricsView biometrics() {
        return biometrics.get();
    }

    @PutMapping("/biometrics")
    public BiometricsView setBiometrics(@Valid @RequestBody BiometricsRequest req) {
        return biometrics.update(req);
    }

    @GetMapping("/preferences")
    public PreferencesView preferences() {
        return preferences.get();
    }

    @PutMapping("/preferences")
    public PreferencesView setPreferences(@Valid @RequestBody PreferencesRequest req) {
        return preferences.update(req);
    }
}
