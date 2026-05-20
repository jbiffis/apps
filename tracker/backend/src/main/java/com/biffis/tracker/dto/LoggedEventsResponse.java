package com.biffis.tracker.dto;

import java.util.List;

public record LoggedEventsResponse(List<LoggedEventView> events, String nextCursor) {
}
