package com.biffis.tracker.exception;

/** Maps to HTTP 404 {"error":"not_found"}. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
