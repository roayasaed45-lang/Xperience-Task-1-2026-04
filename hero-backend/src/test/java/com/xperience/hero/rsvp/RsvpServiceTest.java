package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvitationCreationResult;
import com.xperience.hero.invitation.InvitationService;
import com.xperience.hero.invitation.InvalidInvitationTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests RsvpService.submitOrChangeRsvp only. Does not test waitlist
 * promotion (none exists — Q7/Q13 unresolved), event close/cancel
 * lifecycle beyond the settled I4 "no new submissions once closed" rule,
 * or any HTTP transport concern (see RsvpControllerTest for that).
 */
@SpringBootTest
@Transactional
class RsvpServiceTest {

    @Autowired
    private RsvpService rsvpService;

    @Autowired
    private EventService eventService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private RsvpResponseRepository rsvpResponseRepository;

    @Autowired
    private com.xperience.hero.attendance.AttendanceOutcomeRepository attendanceOutcomeRepository;

    private EventCreationResult createTestEvent(Integer maxCapacity) {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", maxCapacity));
    }

    private EventCreationResult createTestEventStartingAt(LocalDateTime eventDateTime, Integer maxCapacity) {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", eventDateTime, "Main Office", maxCapacity));
    }

    private InvitationCreationResult inviteTo(Long eventId, String hostToken, String email) {
        return invitationService.createInvitation(eventId, hostToken, email);
    }

    @Test
    void invalidTokenIsRejected() {
        assertThrows(InvalidInvitationTokenException.class,
                () -> rsvpService.submitOrChangeRsvp("not-a-real-token", "YES"));
    }

    @Test
    void missingTokenIsRejected() {
        assertThrows(InvalidInvitationTokenException.class,
                () -> rsvpService.submitOrChangeRsvp(null, "YES"));
    }

    @Test
    void invalidResponseValueIsRejected() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        assertThrows(IllegalArgumentException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "MAYBE_NOT_A_REAL_VALUE"));
    }

    @Test
    void tokenResolvesOnlyItsOwnInvitation() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitationA = inviteTo(event.event().getId(), event.rawHostManagementToken(), "a@example.com");
        InvitationCreationResult invitationB = inviteTo(event.event().getId(), event.rawHostManagementToken(), "b@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitationA.rawInvitationToken(), "YES");

        assertEquals(invitationA.invitation().getId(), result.invitation().getId());
        assertNotEquals(invitationB.invitation().getId(), result.invitation().getId());
    }

    @Test
    void firstYesWithCapacityIsConfirmed() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");

        assertEquals(RsvpResponseValue.YES, result.response());
        assertEquals(AttendanceOutcomeValue.CONFIRMED, result.outcome());
    }

    @Test
    void noAlwaysResolvesToOutcomeNone() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "NO");

        assertEquals(RsvpResponseValue.NO, result.response());
        assertEquals(AttendanceOutcomeValue.NONE, result.outcome());
    }

    @Test
    void maybeAlwaysResolvesToOutcomeNone() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "MAYBE");

        assertEquals(RsvpResponseValue.MAYBE, result.response());
        assertEquals(AttendanceOutcomeValue.NONE, result.outcome());
    }

    @Test
    void nullMaxCapacityAlwaysConfirms() {
        EventCreationResult event = createTestEvent(null);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, result.outcome());
    }

    @Test
    void yesBeyondCapacityIsWaitlisted() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult first = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first@example.com");
        InvitationCreationResult second = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second@example.com");

        RsvpSubmissionResult firstResult = rsvpService.submitOrChangeRsvp(first.rawInvitationToken(), "YES");
        RsvpSubmissionResult secondResult = rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "YES");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, firstResult.outcome());
        assertEquals(AttendanceOutcomeValue.WAITLISTED, secondResult.outcome());
    }

    @Test
    void changingResponseUpdatesSameRowsInPlace() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult first = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");
        RsvpSubmissionResult changed = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "NO");

        assertEquals(RsvpResponseValue.NO, changed.response());
        assertEquals(AttendanceOutcomeValue.NONE, changed.outcome());

        assertEquals(1, rsvpResponseRepository.findByInvitation(invitation.invitation()).size());
        assertEquals(1, attendanceOutcomeRepository.findByInvitation(invitation.invitation()).size());
    }

    @Test
    void ownExistingYesExcludedFromOwnCapacityCheck() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");
        RsvpSubmissionResult resubmitted = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, resubmitted.outcome());
    }

    @Test
    void rsvpAtOrAfterEventStartIsRejected() {
        EventCreationResult event = createTestEventStartingAt(LocalDateTime.now().minusMinutes(1), 10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        assertThrows(RsvpLockedException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES"));
    }

    @Test
    void rsvpBeforeEventStartIsAllowed() {
        EventCreationResult event = createTestEventStartingAt(LocalDateTime.now().plusMinutes(5), 10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");

        assertEquals(RsvpResponseValue.YES, result.response());
    }

    @Test
    void secondSubmissionDoesNotCreateDuplicateRows() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "MAYBE");
        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "NO");

        assertEquals(1, rsvpResponseRepository.findByInvitation(invitation.invitation()).size());
        assertEquals(1, attendanceOutcomeRepository.findByInvitation(invitation.invitation()).size());
    }
}
