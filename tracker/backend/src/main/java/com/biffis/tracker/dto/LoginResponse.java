package com.biffis.tracker.dto;

public record LoginResponse(String token, UserView user) {
}
