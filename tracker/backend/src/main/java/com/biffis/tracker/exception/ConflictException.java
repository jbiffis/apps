package com.biffis.tracker.exception;

/** Maps to HTTP 409 {"error":"conflict"}. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
