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
 * NONE. Capacity/waitlist decisions for YES are serialized via the existing
 * per-event pessimistic lock (Section 10 First-Pass Concurrency Decision) —
 * not a new locking mechanism.
 *
 * Implements the resolved time lock (I2 first-pass rule): RSVP creation or
 * change is rejected once the server clock is no longer strictly before the
 * event's eventDateTime. DESIGN.md Q12 (broader timezone policy) remains
 * open and is not addressed by this comparison.
 *
 * Deliberately NOT implemented here: waitlist promotion when a CONFIRMED
 * invitee later changes away from YES (Q7/Q13, unresolved — this slice only
 * ever reduces or leaves unchanged the CONFIRMED count, it never triggers a
 * promotion of a different, WAITLISTED invitation); whether Close blocks
 * changes to an existing RSVP (Q14, unresolved — only brand-new submissions
 * are blocked on a CLOSED event); any check against CANCELLED (I5/W13,
 * unresolved, no control asserted).
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

        Optional<RsvpResponse> existingResponse = resolveAtMostOne(
                rsvpResponseRepository.findByInvitation(invitation), invitation.getId(), "RsvpResponse");

        if (existingResponse.isEmpty() && event.getStatus() == EventStatus.CLOSED) {
            throw new EventClosedException();
        }

        Optional<AttendanceOutcome> existingOutcome = resolveAtMostOne(
                attendanceOutcomeRepository.findByInvitation(invitation), invitation.getId(), "AttendanceOutcome");

        AttendanceOutcomeValue outcomeValue;
        if (requestedResponse == RsvpResponseValue.YES) {
            eventService.lockForCapacityDecision(event.getId());
            outcomeValue = resolveCapacityOutcome(event, invitation.getId());
        } else {
            outcomeValue = AttendanceOutcomeValue.NONE;
        }

        RsvpResponse response = existingResponse.orElseGet(() -> RsvpResponse.builder().invitation(invitation).build());
        response.setResponse(requestedResponse);
        rsvpResponseRepository.save(response);

        AttendanceOutcome outcome = existingOutcome.orElseGet(() -> AttendanceOutcome.builder().invitation(invitation).build());
        outcome.setOutcome(outcomeValue);
        attendanceOutcomeRepository.save(outcome);

        return new RsvpSubmissionResult(invitation, requestedResponse, outcomeValue);
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
