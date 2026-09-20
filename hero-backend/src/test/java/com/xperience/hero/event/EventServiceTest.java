package com.xperience.hero.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests event creation and the host management token (DESIGN.md Q3 —
 * "Resolved Decisions"). Does not test invitations, RSVP, capacity/waitlist,
 * dashboard, or close/cancel — none of those are implemented in this slice.
 */
@SpringBootTest
@Transactional
class EventServiceTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private EventRepository eventRepository;

    private CreateEventRequest validRequest() {
        return new CreateEventRequest(
                "Team Offsite",
                "Quarterly planning session",
                LocalDateTime.now().plusDays(7),
                "Main Office",
                10
        );
    }

    @Test
    void createsEventWithOpenStatus() {
        EventCreationResult result = eventService.createEvent(validRequest());

        assertNotNull(result.event().getId());
        assertEquals(EventStatus.OPEN, result.event().getStatus());
    }

    @Test
    void generatesNonEmptyHostToken() {
        EventCreationResult result = eventService.createEvent(validRequest());

        assertNotNull(result.rawHostManagementToken());
        assertFalse(result.rawHostManagementToken().isBlank());
    }

    @Test
    void rawTokenIsNotStoredDirectly() {
        EventCreationResult result = eventService.createEvent(validRequest());

        Event persisted = eventRepository.findById(result.event().getId()).orElseThrow();
        assertNotEquals(result.rawHostManagementToken(), persisted.getHostTokenHash());
    }

    @Test
    void storedTokenHashExists() {
        EventCreationResult result = eventService.createEvent(validRequest());

        Event persisted = eventRepository.findById(result.event().getId()).orElseThrow();
        assertNotNull(persisted.getHostTokenHash());
        assertFalse(persisted.getHostTokenHash().isBlank());
    }

    @Test
    void correctTokenVerifiesSuccessfully() {
        EventCreationResult result = eventService.createEvent(validRequest());

        boolean verified = eventService.verifyHostToken(result.event().getId(), result.rawHostManagementToken());
        assertTrue(verified);
    }

    @Test
    void incorrectTokenFailsVerification() {
        EventCreationResult result = eventService.createEvent(validRequest());

        boolean verified = eventService.verifyHostToken(result.event().getId(), "not-the-real-token");
        assertFalse(verified);
    }

    @Test
    void twoEventsReceiveDifferentTokens() {
        EventCreationResult first = eventService.createEvent(validRequest());
        EventCreationResult second = eventService.createEvent(validRequest());

        assertNotEquals(first.rawHostManagementToken(), second.rawHostManagementToken());
    }

    @Test
    void manyEventsReceiveUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            EventCreationResult result = eventService.createEvent(validRequest());
            tokens.add(result.rawHostManagementToken());
        }

        assertEquals(20, tokens.size());
    }

    @Test
    void allowsNullMaxCapacity() {
        CreateEventRequest request = new CreateEventRequest(
                "Open House", null, LocalDateTime.now().plusDays(3), "Community Hall", null
        );

        EventCreationResult result = eventService.createEvent(request);

        assertNull(result.event().getMaxCapacity());
    }

    @Test
    void rejectsNegativeMaxCapacity() {
        CreateEventRequest request = new CreateEventRequest(
                "Open House", null, LocalDateTime.now().plusDays(3), "Community Hall", -1
        );

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(request));
    }

    @Test
    void rejectsBlankTitle() {
        CreateEventRequest request = new CreateEventRequest(
                "   ", null, LocalDateTime.now().plusDays(3), "Community Hall", 5
        );

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(request));
    }

    @Test
    void rejectsMissingEventDateTime() {
        CreateEventRequest request = new CreateEventRequest(
                "Open House", null, null, "Community Hall", 5
        );

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(request));
    }

    @Test
    void rejectsBlankLocation() {
        CreateEventRequest request = new CreateEventRequest(
                "Open House", null, LocalDateTime.now().plusDays(3), " ", 5
        );

        assertThrows(IllegalArgumentException.class, () -> eventService.createEvent(request));
    }
}
