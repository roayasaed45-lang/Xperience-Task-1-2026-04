package com.xperience.hero.invitation;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Invitation persistence foundation: structural fields, the
 * required Event relationship (I7/I8), and the UNIQUE(event_id,
 * invitee_email) database constraint (Q8/I13) — proven here at the
 * repository level, independent of any service-layer pre-check, since the
 * database constraint (not the pre-check) is the authoritative guard.
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

    @Test
    void enforcesUniqueConstraintOnEventAndNormalizedEmail() {
        Event event = createTestEvent();
        String email = "duplicate@example.com";

        invitationRepository.saveAndFlush(Invitation.builder()
                .event(event)
                .inviteeEmail(email)
                .invitationTokenHash(invitationTokenService.hashToken(invitationTokenService.generateRawToken()))
                .build());

        Invitation secondAttempt = Invitation.builder()
                .event(event)
                .inviteeEmail(email)
                .invitationTokenHash(invitationTokenService.hashToken(invitationTokenService.generateRawToken()))
                .build();

        // Proven directly at the repository level, bypassing any service
        // pre-check entirely, to demonstrate the database constraint itself
        // is what's authoritative (Q8/I13) — not application logic.
        assertThrows(DataIntegrityViolationException.class,
                () -> invitationRepository.saveAndFlush(secondAttempt));
    }

    @Test
    void allowsSameEmailAcrossDifferentEvents() {
        Event firstEvent = createTestEvent();
        Event secondEvent = createTestEvent();
        String email = "shared@example.com";

        invitationRepository.saveAndFlush(Invitation.builder()
                .event(firstEvent)
                .inviteeEmail(email)
                .invitationTokenHash(invitationTokenService.hashToken(invitationTokenService.generateRawToken()))
                .build());

        Invitation onSecondEvent = invitationRepository.saveAndFlush(Invitation.builder()
                .event(secondEvent)
                .inviteeEmail(email)
                .invitationTokenHash(invitationTokenService.hashToken(invitationTokenService.generateRawToken()))
                .build());

        assertNotNull(onSecondEvent.getId());
    }
}
