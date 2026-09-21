package com.xperience.hero.rsvp;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvitationRepository;
import com.xperience.hero.invitation.InvitationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the RSVP Response persistence foundation only: structural fields and
 * the required Invitation relationship. Does not test any create/update
 * workflow (none exists yet — see RsvpResponse's javadoc for why), lock
 * behavior, capacity, or waitlist logic. No test here asserts an answer to
 * Q1 or Q2.
 */
@SpringBootTest
@Transactional
class RsvpResponseRepositoryTest {

    @Autowired
    private RsvpResponseRepository rsvpResponseRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private InvitationTokenService invitationTokenService;

    @Autowired
    private EventService eventService;

    private Invitation createTestInvitation() {
        EventCreationResult eventResult = eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
        Event event = eventResult.event();

        String hash = invitationTokenService.hashToken(invitationTokenService.generateRawToken());

        return invitationRepository.save(Invitation.builder()
                .event(event)
                .inviteeEmail("guest@example.com")
                .invitationTokenHash(hash)
                .build());
    }

    @Test
    void persistsAndReferencesCorrectInvitation() {
        Invitation invitation = createTestInvitation();

        RsvpResponse saved = rsvpResponseRepository.save(RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.YES)
                .build());

        assertNotNull(saved.getId());

        Optional<RsvpResponse> found = rsvpResponseRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(invitation.getId(), found.get().getInvitation().getId());
    }

    @Test
    void persistsYes() {
        Invitation invitation = createTestInvitation();

        RsvpResponse saved = rsvpResponseRepository.save(RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.YES)
                .build());

        assertEquals(RsvpResponseValue.YES,
                rsvpResponseRepository.findById(saved.getId()).orElseThrow().getResponse());
    }

    @Test
    void persistsNo() {
        Invitation invitation = createTestInvitation();

        RsvpResponse saved = rsvpResponseRepository.save(RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.NO)
                .build());

        assertEquals(RsvpResponseValue.NO,
                rsvpResponseRepository.findById(saved.getId()).orElseThrow().getResponse());
    }

    @Test
    void persistsMaybe() {
        Invitation invitation = createTestInvitation();

        RsvpResponse saved = rsvpResponseRepository.save(RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.MAYBE)
                .build());

        assertEquals(RsvpResponseValue.MAYBE,
                rsvpResponseRepository.findById(saved.getId()).orElseThrow().getResponse());
    }

    @Test
    void rejectsNullResponseValue() {
        Invitation invitation = createTestInvitation();

        RsvpResponse rsvpResponse = RsvpResponse.builder()
                .invitation(invitation)
                .response(null)
                .build();

        assertThrows(Exception.class, () -> rsvpResponseRepository.saveAndFlush(rsvpResponse));
    }

    @Test
    void enforcesUniqueConstraintOnInvitation() {
        Invitation invitation = createTestInvitation();

        rsvpResponseRepository.saveAndFlush(RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.YES)
                .build());

        RsvpResponse secondRowSameInvitation = RsvpResponse.builder()
                .invitation(invitation)
                .response(RsvpResponseValue.NO)
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> rsvpResponseRepository.saveAndFlush(secondRowSameInvitation));
    }
}
