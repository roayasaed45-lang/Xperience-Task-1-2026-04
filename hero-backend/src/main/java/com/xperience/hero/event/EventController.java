package com.xperience.hero.event;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Event controller.
 *
 * Implements only POST /api/events (create). Deliberately NOT implemented:
 * invitation, RSVP, dashboard, close, cancel, and editing endpoints. No
 * public token-verification endpoint is exposed — EventService.verifyHostToken
 * is a foundation for later host-only operations, not a route of its own.
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
}
