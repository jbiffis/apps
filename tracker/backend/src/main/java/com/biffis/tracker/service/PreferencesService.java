package com.biffis.tracker.service;

import com.biffis.tracker.dto.PreferencesRequest;
import com.biffis.tracker.dto.PreferencesView;
import com.biffis.tracker.exception.NotFoundException;
import com.biffis.tracker.model.User;
import com.biffis.tracker.repository.UserRepository;
import com.biffis.tracker.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's measurement unit preferences. Presentation only — logged values
 * stay canonical metric; these columns just drive display/entry on the client.
 * Partial update: a null field on the request is left unchanged.
 */
@Service
public class PreferencesService {

    private final UserRepository users;

    public PreferencesService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public PreferencesView get() {
        return toView(currentUser());
    }

    @Transactional
    public PreferencesView update(PreferencesRequest req) {
        User u = currentUser();
        if (req.weightUnit() != null) {
            u.setWeightUnit(req.weightUnit());
        }
        if (req.heightUnit() != null) {
            u.setHeightUnit(req.heightUnit());
        }
        if (req.temperatureUnit() != null) {
            u.setTemperatureUnit(req.temperatureUnit());
        }
        users.save(u);
        return toView(u);
    }

    private PreferencesView toView(User u) {
        return new PreferencesView(u.getWeightUnit(), u.getHeightUnit(), u.getTemperatureUnit());
    }

    private User currentUser() {
        return users.findById(CurrentUser.id())
                .orElseThrow(() -> new NotFoundException("user not found"));
    }
}
