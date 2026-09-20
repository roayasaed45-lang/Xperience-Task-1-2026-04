package com.xperience.hero.event;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Minimum service responsibility for this slice: retrieve an Event.
 *
 * Event creation is deliberately NOT implemented here. Creating an Event
 * requires recording its host (DESIGN.md I8), but host authentication/identity
 * (DESIGN.md Q3) is unresolved, and no minimal representation can be added to
 * Event without implicitly choosing an identity model. Implementing
 * create-event now would either produce a hostless Event (violating I8) or
 * silently invent an identity representation — both are avoided here.
 *
 * Also deliberately NOT implemented: close, cancel, editing, capacity/waitlist
 * logic, invitations, RSVP, dashboard, and any authorization enforcement
 * (Q2/Q3 unresolved).
 */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }
}
