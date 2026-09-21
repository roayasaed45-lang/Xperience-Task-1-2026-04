package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcome;
import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.dashboard.AmbiguousInvitationStateException;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventService;
import com.xperience.hero.event.EventStatus;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvalidInvitationTokenException;
import com.xperience.hero.invitation.InvitationRepository;
import com.xperience.hero.invitation.InvitationTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * RSVP submission/change for an invitee, gated by the invitation token
 * (DESIGN.md Q2 — resolved: the raw Invitation token is the first-pass
 * bearer credential, scoped via I10 to that one Invitation only).
 *
 * Implements the resolved current-state model (Section 4/8): exactly one
 * RsvpResponse row and exactly one AttendanceOutcome row per Invitation,
 * updated in place on every submission/change — never a new row, never
 * history.
 *
 * Implements the resolved capacity rule (Q1): only a YES response
 * participates in capacity; NO/MAYBE always resolve to Attendance Outcome
 * NONE.
 *
 * Implements the resolved time lock (I2 first-pass rule): RSVP creation or
 * change is rejected once the server clock is no longer strictly before the
 * event's eventDateTime. DESIGN.md Q12 (broader timezone policy) remains
 * open and is not addressed by this comparison.
 *
 * Implements the resolved Event lifecycle enforcement (Section 4, Q11/Q14
 * and Cancel behavior): CLOSED and CANCELLED both block every RSVP
 * create/change; only an OPEN event accepts RSVP writes.
 *
 * Implements the resolved waitlist ordering (Q7 — FIFO via
 * AttendanceOutcome.waitlistedAt) and promotion trigger scope (Q13 — any
 * CONFIRMED->NONE transition, i.e. Yes->No or Yes->Maybe, before event
 * start, automatically promotes the earliest-eligible WAITLISTED
 * Invitation). Capacity decisions and promotion both execute inside the
 * same per-event pessimistic lock (Section 10 First-Pass Concurrency
 * Decision) — a single serialized boundary, not two separate mechanisms.
 *
 * Deliberately NOT implemented here: any waitlist behavior beyond the
 * resolved FIFO/trigger rules (e.g. manual reordering); anything tied to
 * Q4 (multiple hosts), Q9 (token expiry), or Q12 (broader timezone policy).
 */
@Service
public class RsvpService {

    private final InvitationRepository invitationRepository;
    private final InvitationTokenService invitationTokenService;
    private final EventService eventService;
    private final RsvpResponseRepository rsvpResponseRepository;
    private final AttendanceOutcomeRepository attendanceOutcomeRepository;

    public RsvpService(InvitationRepository invitationRepository,
                        InvitationTokenService invitationTokenService,
                        EventService eventService,
                        RsvpResponseRepository rsvpResponseRepository,
                        AttendanceOutcomeRepository attendanceOutcomeRepository) {
        this.invitationRepository = invitationRepository;
        this.invitationTokenService = invitationTokenService;
        this.eventService = eventService;
        this.rsvpResponseRepository = rsvpResponseRepository;
        this.attendanceOutcomeRepository = attendanceOutcomeRepository;
    }

    @Transactional
    public RsvpSubmissionResult submitOrChangeRsvp(String rawInvitationToken, String requestedResponseRaw) {
        if (rawInvitationToken == null || rawInvitationToken.isBlank()) {
            throw new InvalidInvitationTokenException();
        }

        RsvpResponseValue requestedResponse = parseResponse(requestedResponseRaw);

        String tokenHash = invitationTokenService.hashToken(rawInvitationToken);
        Invitation invitation = invitationRepository.findByInvitationTokenHash(tokenHash)
                .orElseThrow(InvalidInvitationTokenException::new);

        Event event = invitation.getEvent();

        if (!event.getEventDateTime().isAfter(LocalDateTime.now())) {
            throw new RsvpLockedException();
        }

        if (event.getStatus() == EventStatus.CLOSED) {
            throw new EventClosedException();
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new EventCancelledException();
        }

        Optional<RsvpResponse> existingResponse = resolveAtMostOne(
                rsvpResponseRepository.findByInvitation(invitation), invitation.getId(), "RsvpResponse");

        Optional<AttendanceOutcome> existingOutcome = resolveAtMostOne(
                attendanceOutcomeRepository.findByInvitation(invitation), invitation.getId(), "AttendanceOutcome");

        AttendanceOutcomeValue previousOutcomeValue = existingOutcome.map(AttendanceOutcome::getOutcome).orElse(null);

        boolean capacityRelevant = requestedResponse == RsvpResponseValue.YES
                || previousOutcomeValue == AttendanceOutcomeValue.CONFIRMED;
        if (capacityRelevant) {
            eventService.lockForCapacityDecision(event.getId());
        }

        AttendanceOutcomeValue outcomeValue;
        LocalDateTime waitlistedAt;
        if (requestedResponse == RsvpResponseValue.YES) {
            outcomeValue = resolveCapacityOutcome(event, invitation.getId());
            if (outcomeValue == AttendanceOutcomeValue.WAITLISTED) {
                waitlistedAt = previousOutcomeValue == AttendanceOutcomeValue.WAITLISTED
                        ? existingOutcome.get().getWaitlistedAt()
                        : LocalDateTime.now();
            } else {
                waitlistedAt = null;
            }
        } else {
            outcomeValue = AttendanceOutcomeValue.NONE;
            waitlistedAt = null;
        }

        RsvpResponse response = existingResponse.orElseGet(() -> RsvpResponse.builder().invitation(invitation).build());
        response.setResponse(requestedResponse);
        rsvpResponseRepository.save(response);

        AttendanceOutcome outcome = existingOutcome.orElseGet(() -> AttendanceOutcome.builder().invitation(invitation).build());
        outcome.setOutcome(outcomeValue);
        outcome.setWaitlistedAt(waitlistedAt);
        attendanceOutcomeRepository.save(outcome);

        boolean freedConfirmedSpot = previousOutcomeValue == AttendanceOutcomeValue.CONFIRMED
                && requestedResponse != RsvpResponseValue.YES;
        if (freedConfirmedSpot) {
            promoteNextWaitlisted(event);
        }

        return new RsvpSubmissionResult(invitation, requestedResponse, outcomeValue);
    }

    /**
     * Promotes the earliest-eligible WAITLISTED Invitation for this Event
     * (Q7 FIFO ordering: ascending waitlistedAt, Invitation id tie-breaker)
     * to CONFIRMED, clearing its waitlistedAt. Does not touch its
     * RsvpResponse — the promoted invitee's response remains whatever it
     * already was (always YES, since only a YES response can be WAITLISTED).
     * A no-op if the waitlist is empty. Must be called only while already
     * holding the per-event pessimistic lock (Section 10).
     */
    private void promoteNextWaitlisted(Event event) {
        attendanceOutcomeRepository
                .findFirstByInvitation_EventAndOutcomeOrderByWaitlistedAtAscInvitation_IdAsc(
                        event, AttendanceOutcomeValue.WAITLISTED)
                .ifPresent(promoted -> {
                    promoted.setOutcome(AttendanceOutcomeValue.CONFIRMED);
                    promoted.setWaitlistedAt(null);
                    attendanceOutcomeRepository.save(promoted);
                });
    }

    private AttendanceOutcomeValue resolveCapacityOutcome(Event event, Long invitationId) {
        if (event.getMaxCapacity() == null) {
            return AttendanceOutcomeValue.CONFIRMED;
        }
        long otherConfirmed = attendanceOutcomeRepository.countByInvitation_EventAndOutcomeAndInvitation_IdNot(
                event, AttendanceOutcomeValue.CONFIRMED, invitationId);
        return otherConfirmed < event.getMaxCapacity()
                ? AttendanceOutcomeValue.CONFIRMED
                : AttendanceOutcomeValue.WAITLISTED;
    }

    private RsvpResponseValue parseResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("RSVP response is required");
        }
        try {
            return RsvpResponseValue.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalidValue) {
            throw new IllegalArgumentException("RSVP response must be one of YES, NO, MAYBE");
        }
    }

    private <T> Optional<T> resolveAtMostOne(List<T> rows, Long invitationId, String concept) {
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() > 1) {
            throw new AmbiguousInvitationStateException(invitationId, concept, rows.size());
        }
        return Optional.of(rows.get(0));
    }
}
