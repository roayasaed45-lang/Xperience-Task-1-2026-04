package com.xperience.hero.attendance;

import com.xperience.hero.event.Event;
import com.xperience.hero.invitation.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceOutcomeRepository extends JpaRepository<AttendanceOutcome, Long> {

    /**
     * All AttendanceOutcome rows for every Invitation on one Event, in a
     * single query — used by the host dashboard (read-only) to avoid an
     * N+1 query per invitation.
     */
    List<AttendanceOutcome> findByInvitation_Event(Event event);

    /**
     * The current AttendanceOutcome row(s) for one Invitation. Expected to
     * be at most one (UNIQUE(invitation_id) enforces this at the DB level)
     * — a List, not an Optional, so RsvpService can explicitly detect and
     * refuse to silently resolve the (should-be-impossible) ambiguous case.
     */
    List<AttendanceOutcome> findByInvitation(Invitation invitation);

    /**
     * Count of CONFIRMED outcomes for an Event, excluding one specific
     * Invitation's own row — used to evaluate capacity (I1, Q1 resolved)
     * without double-counting an invitee re-evaluating their own existing
     * Yes. Confirmed attendance is always derived this way, never stored
     * (DESIGN.md Section 8).
     */
    long countByInvitation_EventAndOutcomeAndInvitation_IdNot(Event event, AttendanceOutcomeValue outcome, Long invitationId);

    /**
     * The earliest-eligible WAITLISTED AttendanceOutcome for one Event, per
     * the resolved FIFO waitlist ordering policy (DESIGN.md Section 4, Q7):
     * ascending `waitlistedAt`, with Invitation id as a deterministic
     * tie-breaker. Scoped to exactly one Event — never loads unrelated
     * Events' waitlist state.
     */
    Optional<AttendanceOutcome> findFirstByInvitation_EventAndOutcomeOrderByWaitlistedAtAscInvitation_IdAsc(
            Event event, AttendanceOutcomeValue outcome);
}
