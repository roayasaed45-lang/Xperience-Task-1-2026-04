package com.xperience.hero.invitation;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Invitation persistence foundation only: structural fields and
 * the required Event relationship (I7/I8). Deliberately does NOT test any
 * creation workflow or duplicate-invitation behavior — DESIGN.md Q8 remains
 * unresolved, and no test here creates more than one Invitation for the
 * same (event, email) pair or asserts what should happen if one did.
 */
@SpringBootTest
@Transactional
class InvitationRepositoryTest {

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private InvitationTokenService invitationTokenService;

    @Autowired
    private EventService eventService;

    private Event createTestEvent() {
        EventCreationResult result = eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
        return result.event();
    }

    @Test
    void persistsAndReferencesCorrectEvent() {
        Event event = createTestEvent();
        String hash = invitationTokenService.hashToken(invitationTokenService.generateRawToken());

        Invitation saved = invitationRepository.save(Invitation.builder()
                .event(event)
                .inviteeEmail("guest@example.com")
                .invitationTokenHash(hash)
                .build());

        assertNotNull(saved.getId());

        Optional<Invitation> found = invitationRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(event.getId(), found.get().getEvent().getId());
        assertEquals("guest@example.com", found.get().getInviteeEmail());
    }

    @Test
    void storedTokenHashIsNotTheRawToken() {
        Event event = createTestEvent();
        String rawToken = invitationTokenService.generateRawToken();
        String hash = invitationTokenService.hashToken(rawToken);

        Invitation saved = invitationRepository.save(Invitation.builder()
                .event(event)
                .inviteeEmail("guest@example.com")
                .invitationTokenHash(hash)
                .build());

        Invitation persisted = invitationRepository.findById(saved.getId()).orElseThrow();
        assertNotEquals(rawToken, persisted.getInvitationTokenHash());
        assertEquals(hash, persisted.getInvitationTokenHash());
    }

    @Test
    void rejectsInvitationForNonexistentEvent() {
        Event nonexistentEvent = Event.builder().id(999_999_999L).build();
        String hash = invitationTokenService.hashToken(invitationTokenService.generateRawToken());

        Invitation invitation = Invitation.builder()
                .event(nonexistentEvent)
                .inviteeEmail("guest@example.com")
                .invitationTokenHash(hash)
                .build();

        // Broad exception type on purpose: whether Hibernate's own
        // unsaved-value check or the database foreign-key constraint is
        // what rejects this is an implementation detail — the structural
        // guarantee under test is only that it IS rejected somehow (I7/I8).
        assertThrows(Exception.class, () -> invitationRepository.saveAndFlush(invitation));
    }
}
