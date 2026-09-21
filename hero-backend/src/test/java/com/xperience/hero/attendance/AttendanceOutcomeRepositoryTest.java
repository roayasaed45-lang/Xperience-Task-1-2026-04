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
}
