package com.xperience.hero.dashboard;

import com.xperience.hero.attendance.AttendanceOutcome;
import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventNotFoundException;
import com.xperience.hero.event.EventService;
import com.xperience.hero.event.InvalidHostTokenException;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvitationCreationResult;
import com.xperience.hero.invitation.InvitationRepository;
import com.xperience.hero.invitation.InvitationService;
import com.xperience.hero.rsvp.RsvpResponse;
import com.xperience.hero.rsvp.RsvpResponseRepository;
import com.xperience.hero.rsvp.RsvpResponseValue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the read-only host dashboard: authorization, derived counts, and
 * that it never mutates state. Does not test RSVP submission, capacity, or
 * waitlist logic — none of those exist yet. No test here creates more than
 * one RsvpResponse/AttendanceOutcome row per Invitation, since DESIGN.md
 * leaves that case's "current row" rule unresolved (see
 * AmbiguousInvitationStateException).
 */
@SpringBootTest
@Transactional
class HostDashboardServiceTest {

    @Autowired
    private HostDashboardService hostDashboardService;

    @Autowired
    private EventService eventService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private RsvpResponseRepository rsvpResponseRepository;

    @Autowired
    private AttendanceOutcomeRepository attendanceOutcomeRepository;

    private EventCreationResult createTestEvent() {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
    }

    @Test
    void validHostTokenReturnsDashboard() {
        EventCreationResult event = createTestEvent();

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        assertEquals(event.event().getId(), dashboard.eventId());
        assertEquals("Team Offsite", dashboard.title());
        assertEquals("Main Office", dashboard.location());
    }

    @Test
    void invalidHostTokenRejected() {
        EventCreationResult event = createTestEvent();

        assertThrows(InvalidHostTokenException.class, () ->
                hostDashboardService.getDashboard(event.event().getId(), "not-the-real-token"));
    }

    @Test
    void missingHostTokenRejected() {
        EventCreationResult event = createTestEvent();

        assertThrows(InvalidHostTokenException.class, () ->
                hostDashboardService.getDashboard(event.event().getId(), null));
    }

    @Test
    void missingEventRejected() {
        assertThrows(EventNotFoundException.class, () ->
                hostDashboardService.getDashboard(999_999_999L, "any-token"));
    }

    @Test
    void emptyEventHasZeroCounts() {
        EventCreationResult event = createTestEvent();

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        assertTrue(dashboard.invitations().isEmpty());
        DashboardCounts counts = dashboard.counts();
        assertEquals(0, counts.totalInvited());
        assertEquals(0, counts.yesCount());
        assertEquals(0, counts.noCount());
        assertEquals(0, counts.maybeCount());
        assertEquals(0, counts.noResponseCount());
        assertEquals(0, counts.confirmedCount());
        assertEquals(0, counts.waitlistedCount());
    }

    @Test
    void invitationsAppearWithNormalizedEmail() {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), " Guest@Example.com ");

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        assertEquals(1, dashboard.invitations().size());
        assertEquals("guest@example.com", dashboard.invitations().get(0).inviteeEmail());
    }

    @Test
    void noResponseInvitationReturnsNullRsvpAndOutcome() {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        InvitationDashboardRow row = dashboard.invitations().get(0);
        assertNull(row.rsvpResponse());
        assertNull(row.attendanceOutcome());
        assertEquals(1, dashboard.counts().noResponseCount());
    }

    @Test
    void responseCountsAreDerivedCorrectly() {
        EventCreationResult event = createTestEvent();

        Invitation yesInvitation = createInvitationWithResponse(event, "yes@example.com", RsvpResponseValue.YES);
        createInvitationWithResponse(event, "no@example.com", RsvpResponseValue.NO);
        createInvitationWithResponse(event, "maybe@example.com", RsvpResponseValue.MAYBE);
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "noresponse@example.com");

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        DashboardCounts counts = dashboard.counts();
        assertEquals(4, counts.totalInvited());
        assertEquals(1, counts.yesCount());
        assertEquals(1, counts.noCount());
        assertEquals(1, counts.maybeCount());
        assertEquals(1, counts.noResponseCount());

        InvitationDashboardRow yesRow = dashboard.invitations().stream()
                .filter(r -> r.invitationId().equals(yesInvitation.getId()))
                .findFirst().orElseThrow();
        assertEquals(RsvpResponseValue.YES, yesRow.rsvpResponse());
    }

    @Test
    void outcomeCountsAreDerivedCorrectly() {
        EventCreationResult event = createTestEvent();

        createInvitationWithOutcome(event, "confirmed@example.com", AttendanceOutcomeValue.CONFIRMED);
        createInvitationWithOutcome(event, "waitlisted@example.com", AttendanceOutcomeValue.WAITLISTED);
        createInvitationWithOutcome(event, "none@example.com", AttendanceOutcomeValue.NONE);

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        DashboardCounts counts = dashboard.counts();
        assertEquals(3, counts.totalInvited());
        assertEquals(1, counts.confirmedCount());
        assertEquals(1, counts.waitlistedCount());
    }

    @Test
    void secretsAreNotSerializable() {
        for (Field field : HostDashboardResponse.class.getDeclaredFields()) {
            assertFalse(containsSecretLikeName(field.getName()), "HostDashboardResponse leaks: " + field.getName());
        }
        for (Field field : InvitationDashboardRow.class.getDeclaredFields()) {
            assertFalse(containsSecretLikeName(field.getName()), "InvitationDashboardRow leaks: " + field.getName());
        }
        for (Field field : DashboardCounts.class.getDeclaredFields()) {
            assertFalse(containsSecretLikeName(field.getName()), "DashboardCounts leaks: " + field.getName());
        }
    }

    @Test
    void dashboardRemainsReadableAfterEventClosed() {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        eventService.closeEvent(event.event().getId(), event.rawHostManagementToken());

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        assertEquals(1, dashboard.invitations().size());
    }

    @Test
    void dashboardRemainsReadableAfterEventCancelled() {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
        eventService.cancelEvent(event.event().getId(), event.rawHostManagementToken());

        HostDashboardResponse dashboard = hostDashboardService.getDashboard(
                event.event().getId(), event.rawHostManagementToken());

        assertEquals(1, dashboard.invitations().size());
    }

    @Test
    void dashboardDoesNotMutateDatabaseState() {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        long invitationsBefore = invitationRepository.count();
        long responsesBefore = rsvpResponseRepository.count();
        long outcomesBefore = attendanceOutcomeRepository.count();

        hostDashboardService.getDashboard(event.event().getId(), event.rawHostManagementToken());
        hostDashboardService.getDashboard(event.event().getId(), event.rawHostManagementToken());

        assertEquals(invitationsBefore, invitationRepository.count());
        assertEquals(responsesBefore, rsvpResponseRepository.count());
        assertEquals(outcomesBefore, attendanceOutcomeRepository.count());
    }

    private boolean containsSecretLikeName(String fieldName) {
        String lower = fieldName.toLowerCase();
        return lower.contains("hash") || lower.contains("token") || lower.contains("secret");
    }

    private Invitation createInvitationWithResponse(EventCreationResult event, String email, RsvpResponseValue value) {
        InvitationCreationResult invitationResult = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), email);
        Invitation invitation = invitationResult.invitation();

        rsvpResponseRepository.save(RsvpResponse.builder()
                .invitation(invitation)
                .response(value)
                .build());

        return invitation;
    }

    private Invitation createInvitationWithOutcome(EventCreationResult event, String email, AttendanceOutcomeValue value) {
        InvitationCreationResult invitationResult = invitationService.createInvitation(
                event.event().getId(), event.rawHostManagementToken(), email);
        Invitation invitation = invitationResult.invitation();

        attendanceOutcomeRepository.save(AttendanceOutcome.builder()
                .invitation(invitation)
                .outcome(value)
                .build());

        return invitation;
    }
}
