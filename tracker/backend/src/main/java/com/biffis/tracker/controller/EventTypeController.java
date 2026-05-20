package com.biffis.tracker.controller;

import com.biffis.tracker.dto.CreateEventTypeRequest;
import com.biffis.tracker.dto.EventTypeView;
import com.biffis.tracker.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/event-types")
public class EventTypeController {

    private final CatalogService catalog;

    public EventTypeController(CatalogService catalog) {
        this.catalog = catalog;
    }

    /** Tree, audience-filtered for the caller's gender. {@code ?include=all} bypasses the filter. */
    @GetMapping
    public List<EventTypeView> tree(@RequestParam(name = "include", required = false) String include) {
        return catalog.tree("all".equalsIgnoreCase(include));
    }

    @GetMapping("/{slug}")
    public EventTypeView bySlug(@PathVariable String slug) {
        return catalog.bySlug(slug);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventTypeView create(@Valid @RequestBody CreateEventTypeRequest request) {
        return catalog.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        catalog.delete(id);
    }
}
