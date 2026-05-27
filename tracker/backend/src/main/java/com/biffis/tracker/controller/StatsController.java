package com.biffis.tracker.controller;

import com.biffis.tracker.dto.StatsResponse;
import com.biffis.tracker.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService stats;

    public StatsController(StatsService stats) {
        this.stats = stats;
    }

    @GetMapping
    public StatsResponse summary(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String tz) {
        return stats.summary(days, tz);
    }
}
