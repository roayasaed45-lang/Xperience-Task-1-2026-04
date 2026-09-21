package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventRepository;
import com.xperience.hero.event.EventService;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Genuine multi-thread concurrency test for the capacity decision (DESIGN.md
 * Section 10 First-Pass Concurrency Decision — per-event pessimistic lock).
 *
 * Deliberately NOT @Transactional: test-rollback isolation would prevent the
 * two worker threads from seeing each other's (uncommitted) setup data and
 * from genuinely contending on the same database row. Each created row is
 * cleaned up manually in @AfterEach instead.
 */
@SpringBootTest
class RsvpConcurrencyTest {

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
    private Long invitationAId;
    private Long invitationBId;

    @AfterEach
    void cleanUp() {
        if (invitationAId != null) {
            rsvpResponseRepository.findByInvitation(invitationRepository.findById(invitationAId).orElseThrow())
                    .forEach(rsvpResponseRepository::delete);
            attendanceOutcomeRepository.findByInvitation(invitationRepository.findById(invitationAId).orElseThrow())
                    .forEach(attendanceOutcomeRepository::delete);
        }
        if (invitationBId != null) {
            rsvpResponseRepository.findByInvitation(invitationRepository.findById(invitationBId).orElseThrow())
                    .forEach(rsvpResponseRepository::delete);
            attendanceOutcomeRepository.findByInvitation(invitationRepository.findById(invitationBId).orElseThrow())
                    .forEach(attendanceOutcomeRepository::delete);
        }
        if (invitationAId != null) {
            invitationRepository.deleteById(invitationAId);
        }
        if (invitationBId != null) {
            invitationRepository.deleteById(invitationBId);
        }
        if (eventId != null) {
            eventRepository.deleteById(eventId);
        }
    }

    @Test
    void twoConcurrentYesSubmissionsAtCapacityOneYieldExactlyOneConfirmedAndOneWaitlisted() throws Exception {
        EventCreationResult event = eventService.createEvent(new CreateEventRequest(
                "Concurrency Test Event", "Capacity contention test", LocalDateTime.now().plusDays(7), "Main Office", 1));
        eventId = event.event().getId();

        InvitationCreationResult invitationA = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "concurrent-a@example.com");
        InvitationCreationResult invitationB = invitationService.createInvitation(
                eventId, event.rawHostManagementToken(), "concurrent-b@example.com");
        invitationAId = invitationA.invitation().getId();
        invitationBId = invitationB.invitation().getId();

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        AtomicReference<RsvpSubmissionResult> resultA = new AtomicReference<>();
        AtomicReference<RsvpSubmissionResult> resultB = new AtomicReference<>();

        executor.submit(() -> {
            readyLatch.countDown();
            awaitUninterruptibly(startLatch);
            resultA.set(rsvpService.submitOrChangeRsvp(invitationA.rawInvitationToken(), "YES"));
        });
        executor.submit(() -> {
            readyLatch.countDown();
            awaitUninterruptibly(startLatch);
            resultB.set(rsvpService.submitOrChangeRsvp(invitationB.rawInvitationToken(), "YES"));
        });

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        List<AttendanceOutcomeValue> outcomes = List.of(resultA.get().outcome(), resultB.get().outcome());

        assertEquals(1, outcomes.stream().filter(o -> o == AttendanceOutcomeValue.CONFIRMED).count());
        assertEquals(1, outcomes.stream().filter(o -> o == AttendanceOutcomeValue.WAITLISTED).count());
    }

    private void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
