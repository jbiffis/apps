package com.biffis.tracker.controller;

import com.biffis.tracker.dto.LogEventRequest;
import com.biffis.tracker.dto.LoggedEventView;
import com.biffis.tracker.dto.LoggedEventsResponse;
import com.biffis.tracker.service.LoggedEventService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/logged-events")
public class LoggedEventController {

    private final LoggedEventService service;

    public LoggedEventController(LoggedEventService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoggedEventView create(@Valid @RequestBody LogEventRequest request) {
        return service.create(request);
    }

    @GetMapping
    public LoggedEventsResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String eventTypeSlug,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        return service.list(from, to, eventTypeSlug, limit, cursor);
    }

    @GetMapping("/{id}")
    public LoggedEventView getOne(@PathVariable UUID id) {
        return service.getOne(id);
    }

    @PutMapping("/{id}")
    public LoggedEventView update(@PathVariable UUID id, @Valid @RequestBody LogEventRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/restore")
    public LoggedEventView restore(@PathVariable UUID id) {
        return service.restore(id);
    }
}
