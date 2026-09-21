package com.xperience.hero.invitation;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventNotFoundException;
import com.xperience.hero.event.EventService;
import com.xperience.hero.event.InvalidHostTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the Invitation creation workflow: host authorization (DESIGN.md
 * Q3) and the duplicate-invitation policy (Q8/I13). Does not test RSVP,
 * capacity/waitlist, invitation delivery, or invitee identity (Q2).
 */
@SpringBootTest
@Transactional
class InvitationServiceTest {

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private EventService eventService;

    private EventCreationResult createTestEvent() {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
    }

    @Test
    void createsInvitationForValidHostToken() {
        EventCreationResult event = createTestEvent();

        InvitationCreationResult result = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        assertNotNull(result.invitation().getId());
        assertEquals(event.event().getId(), result.invitation().getEvent().getId());
    }

    @Test
    void storedEmailIsTrimmedAndLowercased() {
        EventCreationResult event = createTestEvent();

        InvitationCreationResult result = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), " Guest@Example.com ");

        assertEquals("guest@example.com", result.invitation().getInviteeEmail());
    }

    @Test
    void rawTokenIsReturned() {
        EventCreationResult event = createTestEvent();

        InvitationCreationResult result = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        assertNotNull(result.rawInvitationToken());
        assertFalse(result.rawInvitationToken().isBlank());
    }

    @Test
    void storedTokenIsHashAndDiffersFromRaw() {
        EventCreationResult event = createTestEvent();

        InvitationCreationResult result = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        Invitation persisted = invitationRepository.findById(result.invitation().getId()).orElseThrow();
        assertNotEquals(result.rawInvitationToken(), persisted.getInvitationTokenHash());
        assertFalse(persisted.getInvitationTokenHash().isBlank());
    }

    @Test
    void invalidHostTokenRejected() {
        EventCreationResult event = createTestEvent();

        assertThrows(InvalidHostTokenException.class, () -> invitationService.createInvitation(
                event.event().getId(), "not-the-real-host-token", "guest@example.com"));
    }

    @Test
    void missingHostTokenRejected() {
        EventCreationResult event = createTestEvent();

        assertThrows(InvalidHostTokenException.class, () -> invitationService.createInvitation(
                event.event().getId(), null, "guest@example.com"));
    }

    @Test
    void nonexistentEventRejected() {
        assertThrows(EventNotFoundException.class, () -> invitationService.createInvitation(
                999_999_999L, "any-token", "guest@example.com"));
    }

    @Test
    void blankEmailRejected() {
        EventCreationResult event = createTestEvent();

        assertThrows(IllegalArgumentException.class, () -> invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "   "));
    }

    @Test
    void duplicateNormalizedEmailForSameEventRejected() {
        EventCreationResult event = createTestEvent();

        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "Guest@Example.com");

        assertThrows(DuplicateInvitationException.class, () -> invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), " guest@example.com "));
    }

    @Test
    void sameNormalizedEmailAllowedForDifferentEvents() {
        EventCreationResult firstEvent = createTestEvent();
        EventCreationResult secondEvent = createTestEvent();

        InvitationCreationResult first = invitationService.createInvitation(
                firstEvent.event().getId(), firstEvent.rawHostManagementToken(), "guest@example.com");
        InvitationCreationResult second = invitationService.createInvitation(
                secondEvent.event().getId(), secondEvent.rawHostManagementToken(), "guest@example.com");

        assertNotEquals(first.invitation().getId(), second.invitation().getId());
    }

    @Test
    void existingInvitationNotOverwrittenOrTokenRotatedOnDuplicate() {
        EventCreationResult event = createTestEvent();

        InvitationCreationResult original = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        String originalHash = original.invitation().getInvitationTokenHash();
        Long originalId = original.invitation().getId();

        assertThrows(DuplicateInvitationException.class, () -> invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), "guest@example.com"));

        Invitation stillThere = invitationRepository.findById(originalId).orElseThrow();
        assertEquals(originalHash, stillThere.getInvitationTokenHash());
    }
}
