package com.xperience.hero.event;

/**
 * Thrown when an operation references an Event id that doesn't exist.
 * Mapped to HTTP 404 by the global exception handler.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(Long eventId) {
        super("Event not found: " + eventId);
    }
}
