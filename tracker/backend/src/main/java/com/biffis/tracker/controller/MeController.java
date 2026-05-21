package com.biffis.tracker.controller;

import com.biffis.tracker.dto.TrackerPrefRequest;
import com.biffis.tracker.dto.TrackerPrefView;
import com.biffis.tracker.service.TrackerPrefService;
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

    public MeController(TrackerPrefService prefs) {
        this.prefs = prefs;
    }

    @GetMapping("/tracker-prefs")
    public List<TrackerPrefView> trackerPrefs() {
        return prefs.list();
    }

    @PutMapping("/tracker-prefs/{slug}")
    public TrackerPrefView setTrackerPref(@PathVariable String slug, @RequestBody TrackerPrefRequest req) {
        return prefs.upsert(slug, req);
    }
}
