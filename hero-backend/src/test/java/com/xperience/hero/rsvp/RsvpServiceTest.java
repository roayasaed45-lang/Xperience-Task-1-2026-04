package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcome;
import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private AttendanceOutcomeRepository attendanceOutcomeRepository;

    @Autowired
    private EventRepository eventRepository;

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

    private AttendanceOutcome outcomeFor(Invitation invitation) {
        return attendanceOutcomeRepository.findByInvitation(invitation).get(0);
    }

    private RsvpResponse responseFor(Invitation invitation) {
        return rsvpResponseRepository.findByInvitation(invitation).get(0);
    }

    // --- Waitlist ordering (Q7) ---

    @Test
    void firstWaitlistedGetsWaitlistedAt() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult first = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first@example.com");
        InvitationCreationResult second = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second@example.com");

        rsvpService.submitOrChangeRsvp(first.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "YES");

        assertNotNull(outcomeFor(second.invitation()).getWaitlistedAt());
        assertNull(outcomeFor(first.invitation()).getWaitlistedAt());
    }

    @Test
    void remainingWaitlistedPreservesWaitlistedAt() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult first = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first@example.com");
        InvitationCreationResult second = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second@example.com");

        rsvpService.submitOrChangeRsvp(first.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "YES");
        LocalDateTime originalWaitlistedAt = outcomeFor(second.invitation()).getWaitlistedAt();

        rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "YES");

        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(second.invitation()).getOutcome());
        assertEquals(originalWaitlistedAt, outcomeFor(second.invitation()).getWaitlistedAt());
    }

    @Test
    void leavingWaitlistedClearsWaitlistedAt() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult first = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first@example.com");
        InvitationCreationResult second = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second@example.com");

        rsvpService.submitOrChangeRsvp(first.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "YES");
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(second.invitation()).getOutcome());

        rsvpService.submitOrChangeRsvp(second.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.NONE, outcomeFor(second.invitation()).getOutcome());
        assertNull(outcomeFor(second.invitation()).getWaitlistedAt());
    }

    @Test
    void tieBreaksByInvitationIdWhenWaitlistedAtEqual() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult lowerId = inviteTo(event.event().getId(), event.rawHostManagementToken(), "lower@example.com");
        InvitationCreationResult higherId = inviteTo(event.event().getId(), event.rawHostManagementToken(), "higher@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(lowerId.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(higherId.rawInvitationToken(), "YES");

        LocalDateTime sameInstant = LocalDateTime.now();
        AttendanceOutcome lowerOutcome = outcomeFor(lowerId.invitation());
        lowerOutcome.setWaitlistedAt(sameInstant);
        attendanceOutcomeRepository.save(lowerOutcome);
        AttendanceOutcome higherOutcome = outcomeFor(higherId.invitation());
        higherOutcome.setWaitlistedAt(sameInstant);
        attendanceOutcomeRepository.save(higherOutcome);

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(lowerId.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(higherId.invitation()).getOutcome());
    }

    // --- Promotion trigger (Q13) ---

    @Test
    void confirmedYesToNoPromotesFirstWaiter() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(waiting.invitation()).getOutcome());
        assertNull(outcomeFor(waiting.invitation()).getWaitlistedAt());
    }

    @Test
    void confirmedYesToMaybePromotesFirstWaiter() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "MAYBE");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(waiting.invitation()).getOutcome());
    }

    @Test
    void promotedInviteeResponseRemainsYes() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "NO");

        assertEquals(RsvpResponseValue.YES, responseFor(waiting.invitation()).getResponse());
    }

    @Test
    void onlyOneWaiterPromotedPerFreedSpot() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult firstWaiter = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first-waiter@example.com");
        InvitationCreationResult secondWaiter = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second-waiter@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(firstWaiter.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(secondWaiter.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(firstWaiter.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(secondWaiter.invitation()).getOutcome());
    }

    @Test
    void noPromotionWhenNoWaiterExists() {
        EventCreationResult event = createTestEvent(2);
        InvitationCreationResult a = inviteTo(event.event().getId(), event.rawHostManagementToken(), "a@example.com");
        InvitationCreationResult b = inviteTo(event.event().getId(), event.rawHostManagementToken(), "b@example.com");

        rsvpService.submitOrChangeRsvp(a.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(b.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(a.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(b.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.NONE, outcomeFor(a.invitation()).getOutcome());
    }

    @Test
    void noPromotionOnNoToNo() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");
        InvitationCreationResult declining = inviteTo(event.event().getId(), event.rawHostManagementToken(), "declining@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(declining.rawInvitationToken(), "NO");

        rsvpService.submitOrChangeRsvp(declining.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(waiting.invitation()).getOutcome());
    }

    @Test
    void noPromotionOnMaybeToMaybe() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");
        InvitationCreationResult maybeInvitee = inviteTo(event.event().getId(), event.rawHostManagementToken(), "maybe@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(maybeInvitee.rawInvitationToken(), "MAYBE");

        rsvpService.submitOrChangeRsvp(maybeInvitee.rawInvitationToken(), "MAYBE");

        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(waiting.invitation()).getOutcome());
    }

    @Test
    void noPromotionOnYesToYes() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");

        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(confirmed.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(waiting.invitation()).getOutcome());
    }

    @Test
    void noPromotionWhenWaitlistedInviteeLeaves() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult firstWaiter = inviteTo(event.event().getId(), event.rawHostManagementToken(), "first-waiter@example.com");
        InvitationCreationResult secondWaiter = inviteTo(event.event().getId(), event.rawHostManagementToken(), "second-waiter@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(firstWaiter.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(secondWaiter.rawInvitationToken(), "YES");

        rsvpService.submitOrChangeRsvp(firstWaiter.rawInvitationToken(), "NO");

        assertEquals(AttendanceOutcomeValue.NONE, outcomeFor(firstWaiter.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(secondWaiter.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(confirmed.invitation()).getOutcome());
    }

    @Test
    void noPromotionAfterEventStart() {
        EventCreationResult event = createTestEvent(1);
        InvitationCreationResult confirmed = inviteTo(event.event().getId(), event.rawHostManagementToken(), "confirmed@example.com");
        InvitationCreationResult waiting = inviteTo(event.event().getId(), event.rawHostManagementToken(), "waiting@example.com");

        rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting.rawInvitationToken(), "YES");
        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(waiting.invitation()).getOutcome());

        // Move the event's start time into the past, simulating "event already
        // started" without needing to sleep past a real deadline.
        Event persistedEvent = eventRepository.findById(event.event().getId()).orElseThrow();
        persistedEvent.setEventDateTime(LocalDateTime.now().minusMinutes(1));
        eventRepository.save(persistedEvent);

        assertThrows(RsvpLockedException.class,
                () -> rsvpService.submitOrChangeRsvp(confirmed.rawInvitationToken(), "NO"));

        assertEquals(AttendanceOutcomeValue.WAITLISTED, outcomeFor(waiting.invitation()).getOutcome());
        assertEquals(AttendanceOutcomeValue.CONFIRMED, outcomeFor(confirmed.invitation()).getOutcome());
    }

    // --- Event lifecycle enforcement (Q11/Q14, Cancel behavior) ---

    @Test
    void closedEventRejectsNewRsvp() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        eventService.closeEvent(event.event().getId(), event.rawHostManagementToken());

        assertThrows(EventClosedException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES"));
    }

    @Test
    void closedEventRejectsChangeToExistingRsvp() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");
        eventService.closeEvent(event.event().getId(), event.rawHostManagementToken());

        assertThrows(EventClosedException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "NO"));
    }

    @Test
    void cancelledEventRejectsNewRsvp() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        eventService.cancelEvent(event.event().getId(), event.rawHostManagementToken());

        assertThrows(EventCancelledException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES"));
    }

    @Test
    void cancelledEventRejectsChangeToExistingRsvp() {
        EventCreationResult event = createTestEvent(10);
        InvitationCreationResult invitation = inviteTo(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "YES");
        eventService.cancelEvent(event.event().getId(), event.rawHostManagementToken());

        assertThrows(EventCancelledException.class,
                () -> rsvpService.submitOrChangeRsvp(invitation.rawInvitationToken(), "NO"));
    }
}
