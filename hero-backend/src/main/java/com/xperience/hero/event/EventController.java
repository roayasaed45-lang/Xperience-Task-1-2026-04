package com.xperience.hero.event;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Event controller.
 *
 * Implements POST /api/events (create) and the host-authorized lifecycle
 * endpoints PUT /api/events/{eventId}/close and
 * PUT /api/events/{eventId}/cancel (DESIGN.md Section 4 — Q6/Q11/Q14 and
 * Cancel behavior, resolved). Deliberately NOT implemented: invitation,
 * RSVP, dashboard, and editing endpoints (those live in their own
 * controllers, or remain unimplemented). No public token-verification
 * endpoint is exposed — EventService.verifyHostToken is a foundation for
 * host-only operations, not a route of its own.
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<CreateEventResponse> createEvent(@RequestBody CreateEventRequest request) {
        EventCreationResult result = eventService.createEvent(request);
        Event event = result.event();

        CreateEventResponse response = new CreateEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDateTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getStatus(),
                result.rawHostManagementToken()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{eventId}/close")
    public ResponseEntity<EventLifecycleResponse> closeEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Host-Management-Token", required = false) String hostManagementToken) {

        Event event = eventService.closeEvent(eventId, hostManagementToken);
        return ResponseEntity.ok(toLifecycleResponse(event));
    }

    @PutMapping("/{eventId}/cancel")
    public ResponseEntity<EventLifecycleResponse> cancelEvent(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Host-Management-Token", required = false) String hostManagementToken) {

        Event event = eventService.cancelEvent(eventId, hostManagementToken);
        return ResponseEntity.ok(toLifecycleResponse(event));
    }

    private EventLifecycleResponse toLifecycleResponse(Event event) {
        return new EventLifecycleResponse(event.getId(), event.getStatus(), event.getTitle(), event.getEventDateTime());
    }
}
