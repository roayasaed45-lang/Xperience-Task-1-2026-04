package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcome;
import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventRepository;
import com.xperience.hero.event.EventService;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvitationCreationResult;
import com.xperience.hero.invitation.InvitationRepository;
import com.xperience.hero.invitation.InvitationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genuine multi-thread concurrency test for waitlist promotion (DESIGN.md
 * Section 4, Q13 resolved: promotion executes inside the same per-event
 * pessimistic lock used for the capacity decision — Section 10).
 *
 * Two CONFIRMED invitees concurrently free their spot (Yes->No and
 * Yes->Maybe) while two invitees are WAITLISTED. Since both frees are
 * serialized through the same per-event lock, each must trigger exactly one
 * promotion of a distinct waitlisted invitee — never the same waiter
 * promoted twice, never both frees racing to promote only one waiter.
 *
 * Deliberately NOT @Transactional, for the same reason as
 * RsvpConcurrencyTest: rollback isolation would prevent genuine multi-thread
 * contention on the same event row. Cleanup is manual.
 */
@SpringBootTest
class RsvpPromotionConcurrencyTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private RsvpService rsvpService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private RsvpResponseRepository rsvpResponseRepository;

    @Autowired
    private AttendanceOutcomeRepository attendanceOutcomeRepository;

    private Long eventId;
    private final List<Long> invitationIds = new java.util.ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long invitationId : invitationIds) {
            Invitation invitation = invitationRepository.findById(invitationId).orElseThrow();
            rsvpResponseRepository.findByInvitation(invitation).forEach(rsvpResponseRepository::delete);
            attendanceOutcomeRepository.findByInvitation(invitation).forEach(attendanceOutcomeRepository::delete);
        }
        for (Long invitationId : invitationIds) {
            invitationRepository.deleteById(invitationId);
        }
        if (eventId != null) {
            eventRepository.deleteById(eventId);
        }
    }

    @Test
    void twoConcurrentFreedSpotsPromoteTwoDistinctWaitersExactlyOnce() throws Exception {
        EventCreationResult event = eventService.createEvent(new CreateEventRequest(
                "Promotion Concurrency Test Event", "Promotion contention test", LocalDateTime.now().plusDays(7), "Main Office", 2));
        eventId = event.event().getId();

        InvitationCreationResult confirmed1 = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "confirmed1@example.com");
        InvitationCreationResult confirmed2 = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "confirmed2@example.com");
        InvitationCreationResult waiting1 = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "waiting1@example.com");
        InvitationCreationResult waiting2 = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "waiting2@example.com");
        invitationIds.add(confirmed1.invitation().getId());
        invitationIds.add(confirmed2.invitation().getId());
        invitationIds.add(waiting1.invitation().getId());
        invitationIds.add(waiting2.invitation().getId());

        rsvpService.submitOrChangeRsvp(confirmed1.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(confirmed2.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting1.rawInvitationToken(), "YES");
        rsvpService.submitOrChangeRsvp(waiting2.rawInvitationToken(), "YES");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            readyLatch.countDown();
            awaitUninterruptibly(startLatch);
            rsvpService.submitOrChangeRsvp(confirmed1.rawInvitationToken(), "NO");
        });
        executor.submit(() -> {
            readyLatch.countDown();
            awaitUninterruptibly(startLatch);
            rsvpService.submitOrChangeRsvp(confirmed2.rawInvitationToken(), "MAYBE");
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        AttendanceOutcome waiting1Outcome = attendanceOutcomeRepository.findByInvitation(waiting1.invitation()).get(0);
        AttendanceOutcome waiting2Outcome = attendanceOutcomeRepository.findByInvitation(waiting2.invitation()).get(0);

        assertEquals(AttendanceOutcomeValue.CONFIRMED, waiting1Outcome.getOutcome());
        assertEquals(AttendanceOutcomeValue.CONFIRMED, waiting2Outcome.getOutcome());

        long confirmedCount = attendanceOutcomeRepository.countByInvitation_EventAndOutcomeAndInvitation_IdNot(
                event.event(), AttendanceOutcomeValue.CONFIRMED, -1L);
        assertEquals(2, confirmedCount);
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
