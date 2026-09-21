package com.xperience.hero.event;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Event business logic.
 *
 * Implements event creation (with host management token generation,
 * DESIGN.md Q3 — "Resolved Decisions"), the read/verification support other
 * host-only operations need, and the host-authorized lifecycle operations
 * Close and Cancel (DESIGN.md Section 4, Q6/Q11/Q14 and Cancel behavior
 * resolved): only `OPEN -> CLOSED` and `OPEN -> CANCELLED` are valid
 * transitions — there is no reopen and no cross-transition between CLOSED
 * and CANCELLED.
 *
 * Deliberately NOT implemented here: invitations, RSVP, dashboard, editing,
 * or any invitee identity handling (Q2 is resolved elsewhere, in
 * InvitationTokenService/RsvpService).
 */
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final HostTokenService hostTokenService;

    public EventService(EventRepository eventRepository, HostTokenService hostTokenService) {
        this.eventRepository = eventRepository;
        this.hostTokenService = hostTokenService;
    }

    /**
     * Creates an Event and generates its host management token in one
     * transactional operation (DESIGN.md I8 / Section 10 Transaction
     * Boundaries item 3). Status, host token, and host token hash are
     * always server-determined and never accepted from the request.
     */
    @Transactional
    public EventCreationResult createEvent(CreateEventRequest request) {
        validate(request);

        String rawToken = hostTokenService.generateRawToken();
        String tokenHash = hostTokenService.hashToken(rawToken);

        Event event = Event.builder()
                .title(request.title())
                .description(request.description())
                .eventDateTime(request.eventDateTime())
                .location(request.location())
                .maxCapacity(request.maxCapacity())
                .status(EventStatus.OPEN)
                .hostTokenHash(tokenHash)
                .build();

        Event saved = eventRepository.save(event);

        return new EventCreationResult(saved, rawToken);
    }

    public Optional<Event> getEventById(Long id) {
        return eventRepository.findById(id);
    }

    /**
     * Verifies a caller-supplied raw host management token against the
     * stored hash for the given event. Foundation for later host-only
     * operations (invite, dashboard, close, cancel) — none of those
     * operations are implemented yet, and this is not exposed as a public
     * endpoint (DESIGN.md Section 9).
     */
    public boolean verifyHostToken(Long eventId, String rawToken) {
        return eventRepository.findById(eventId)
                .map(event -> hostTokenService.verify(rawToken, event.getHostTokenHash()))
                .orElse(false);
    }

    /**
     * Acquires the per-event pessimistic lock used to serialize capacity
     * decisions (DESIGN.md Section 10 First-Pass Concurrency Decision).
     * Must be called from within an active transaction that also performs
     * the capacity read and Attendance Outcome write it is meant to guard.
     */
    public void lockForCapacityDecision(Long eventId) {
        eventRepository.lockForCapacityDecision(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
    }

    /**
     * Closes an Event (DESIGN.md Section 4, Q11/Q14 resolved): only a
     * currently-OPEN event may transition to CLOSED. Any other current
     * status (already CLOSED, or CANCELLED) is an invalid transition — there
     * is no reopen and no cross-transition between CLOSED and CANCELLED
     * (Q6 resolved).
     */
    @Transactional
    public Event closeEvent(Long eventId, String hostManagementToken) {
        return transitionStatus(eventId, hostManagementToken, EventStatus.CLOSED);
    }

    /**
     * Cancels an Event (DESIGN.md Section 4, Cancel behavior resolved): only
     * a currently-OPEN event may transition to CANCELLED. Any other current
     * status is an invalid transition, per the same rules as closeEvent.
     */
    @Transactional
    public Event cancelEvent(Long eventId, String hostManagementToken) {
        return transitionStatus(eventId, hostManagementToken, EventStatus.CANCELLED);
    }

    private Event transitionStatus(Long eventId, String hostManagementToken, EventStatus targetStatus) {
        if (hostManagementToken == null || hostManagementToken.isBlank()) {
            throw new InvalidHostTokenException();
        }

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!hostTokenService.verify(hostManagementToken, event.getHostTokenHash())) {
            throw new InvalidHostTokenException();
        }

        if (event.getStatus() != EventStatus.OPEN) {
            throw new InvalidEventTransitionException(event.getStatus(), targetStatus);
        }

        event.setStatus(targetStatus);
        return eventRepository.save(event);
    }

    private void validate(CreateEventRequest request) {
        if (request.title() == null || request.title().isBlank()) {
            throw new IllegalArgumentException("Event title is required");
        }
        if (request.eventDateTime() == null) {
            throw new IllegalArgumentException("Event date/time is required");
        }
        if (request.location() == null || request.location().isBlank()) {
            throw new IllegalArgumentException("Event location is required");
        }
        if (request.maxCapacity() != null && request.maxCapacity() < 0) {
            throw new IllegalArgumentException("Event max capacity must not be negative");
        }
    }
}
