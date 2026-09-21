package com.xperience.hero.attendance;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Attendance Outcome persistence foundation only: structural
 * fields and the required Invitation relationship (DESIGN.md Section 4/8 —
 * resolved ownership decision). Does not test any create/update workflow,
 * capacity evaluation, or waitlist promotion — none exist yet. No test here
 * asserts an answer to Q1, Q2, Q7, or Q13.
 */
@SpringBootTest
@Transactional
class AttendanceOutcomeRepositoryTest {

    @Autowired
    private AttendanceOutcomeRepository attendanceOutcomeRepository;

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

        return createInvitationForEvent(event, "guest@example.com");
    }

    private Invitation createInvitationForEvent(Event event, String email) {
        String hash = invitationTokenService.hashToken(invitationTokenService.generateRawToken());

        return invitationRepository.save(Invitation.builder()
                .event(event)
                .inviteeEmail(email)
                .invitationTokenHash(hash)
                .build());
    }

    @Test
    void persistsAndReferencesCorrectInvitation() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.NONE)
                .build());

        assertNotNull(saved.getId());

        Optional<AttendanceOutcome> found = attendanceOutcomeRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals(invitation.getId(), found.get().getInvitation().getId());
    }

    @Test
    void persistsConfirmed() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.CONFIRMED)
                .build());

        assertEquals(AttendanceOutcomeValue.CONFIRMED,
                attendanceOutcomeRepository.findById(saved.getId()).orElseThrow().getOutcome());
    }

    @Test
    void persistsWaitlisted() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.WAITLISTED)
                .build());

        assertEquals(AttendanceOutcomeValue.WAITLISTED,
                attendanceOutcomeRepository.findById(saved.getId()).orElseThrow().getOutcome());
    }

    @Test
    void persistsNone() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.NONE)
                .build());

        assertEquals(AttendanceOutcomeValue.NONE,
                attendanceOutcomeRepository.findById(saved.getId()).orElseThrow().getOutcome());
    }

    @Test
    void rejectsNullOutcome() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome attendanceOutcome = AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(null)
                .build();

        assertThrows(Exception.class, () -> attendanceOutcomeRepository.saveAndFlush(attendanceOutcome));
    }

    @Test
    void rejectsNullInvitation() {
        AttendanceOutcome attendanceOutcome = AttendanceOutcome.builder()
                .invitation(null)
                .outcome(AttendanceOutcomeValue.NONE)
                .build();

        assertThrows(Exception.class, () -> attendanceOutcomeRepository.saveAndFlush(attendanceOutcome));
    }

    @Test
    void enforcesUniqueConstraintOnInvitation() {
        Invitation invitation = createTestInvitation();

        attendanceOutcomeRepository.saveAndFlush(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.CONFIRMED)
                .build());

        AttendanceOutcome secondRowSameInvitation = AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.NONE)
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> attendanceOutcomeRepository.saveAndFlush(secondRowSameInvitation));
    }

    @Test
    void waitlistedAtDefaultsToNull() {
        Invitation invitation = createTestInvitation();

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.CONFIRMED)
                .build());

        assertNull(attendanceOutcomeRepository.findById(saved.getId()).orElseThrow().getWaitlistedAt());
    }

    @Test
    void persistsAndReadsBackWaitlistedAt() {
        Invitation invitation = createTestInvitation();
        LocalDateTime waitlistedAt = LocalDateTime.now().withNano(0);

        AttendanceOutcome saved = attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(AttendanceOutcomeValue.WAITLISTED)
                .waitlistedAt(waitlistedAt)
                .build());

        assertEquals(waitlistedAt, attendanceOutcomeRepository.findById(saved.getId()).orElseThrow().getWaitlistedAt());
    }

    @Test
    void findsFirstWaitlistedByEventOrderedByWaitlistedAtThenInvitationId() {
        EventCreationResult eventResult = eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 1
        ));
        Event event = eventResult.event();

        Invitation later = createInvitationForEvent(event, "later@example.com");
        Invitation earlier = createInvitationForEvent(event, "earlier@example.com");

        LocalDateTime now = LocalDateTime.now();
        attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(later)
                .outcome(AttendanceOutcomeValue.WAITLISTED)
                .waitlistedAt(now.plusMinutes(5))
                .build());
        attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(earlier)
                .outcome(AttendanceOutcomeValue.WAITLISTED)
                .waitlistedAt(now)
                .build());

        Optional<AttendanceOutcome> first = attendanceOutcomeRepository
                .findFirstByInvitation_EventAndOutcomeOrderByWaitlistedAtAscInvitation_IdAsc(
                        event, AttendanceOutcomeValue.WAITLISTED);

        assertTrue(first.isPresent());
        assertEquals(earlier.getId(), first.get().getInvitation().getId());
    }

    @Test
    void findFirstWaitlistedScopedToOneEventOnly() {
        EventCreationResult eventAResult = eventService.createEvent(new CreateEventRequest(
                "Event A", null, LocalDateTime.now().plusDays(7), "Main Office", 1
        ));
        EventCreationResult eventBResult = eventService.createEvent(new CreateEventRequest(
                "Event B", null, LocalDateTime.now().plusDays(7), "Main Office", 1
        ));

        Invitation invitationOnEventB = createInvitationForEvent(eventBResult.event(), "guest@example.com");
        attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitationOnEventB)
                .outcome(AttendanceOutcomeValue.WAITLISTED)
                .waitlistedAt(LocalDateTime.now())
                .build());

        Optional<AttendanceOutcome> firstForEventA = attendanceOutcomeRepository
                .findFirstByInvitation_EventAndOutcomeOrderByWaitlistedAtAscInvitation_IdAsc(
                        eventAResult.event(), AttendanceOutcomeValue.WAITLISTED);

        assertEquals(Optional.empty(), firstForEventA);
    }
}
