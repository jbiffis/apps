package com.biffis.tracker.controller;

import com.biffis.tracker.dto.HeroCard;
import com.biffis.tracker.dto.LoggedEventView;
import com.biffis.tracker.service.HomeService;
import com.biffis.tracker.service.LoggedEventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private static final int TODAY_MAX = 10;

    private final HomeService homeService;
    private final LoggedEventService loggedEvents;

    public HomeController(HomeService homeService, LoggedEventService loggedEvents) {
        this.homeService = homeService;
        this.loggedEvents = loggedEvents;
    }

    @GetMapping("/hero")
    public List<HeroCard> hero() {
        return homeService.hero();
    }

    @GetMapping("/today")
    public List<LoggedEventView> today() {
        return loggedEvents.today(TODAY_MAX);
    }
}
