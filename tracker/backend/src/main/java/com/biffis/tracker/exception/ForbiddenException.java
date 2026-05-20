package com.biffis.tracker.exception;

/** Maps to HTTP 403 {"error":"forbidden"}. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
