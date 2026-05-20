package com.biffis.tracker.controller;

import com.biffis.tracker.dto.PresetView;
import com.biffis.tracker.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/property-presets")
public class PropertyPresetController {

    private final CatalogService catalog;

    public PropertyPresetController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<PresetView> all() {
        return catalog.allPresets();
    }
}
