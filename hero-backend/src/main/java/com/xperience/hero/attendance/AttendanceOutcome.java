package com.xperience.hero.attendance;

import com.xperience.hero.invitation.Invitation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Attendance Outcome domain entity, per DESIGN.md Section 8 (Data Ownership
 * and State Model) and the resolved ownership decision (Section 4,
 * "Resolved Decisions" — Attendance Outcome ownership).
 *
 * Belongs directly to exactly one Invitation. It is a SIBLING state concept
 * to RSVP Response, not a child of RsvpResponse — this entity does not
 * reference RsvpResponse at all, even though its value is conceptually
 * derived from the current Response plus capacity state (a computation
 * relationship, not a storage/ownership one).
 *
 * DESIGN.md's current-state persistence model (Section 4/8, Resolved
 * Decision) is now settled: each Invitation has exactly one current
 * AttendanceOutcome row, enforced by UNIQUE(invitation_id) below. A
 * capacity/promotion-driven change updates this row in place — RsvpService
 * never inserts a second row for the same Invitation. This is current-state
 * only, not history.
 *
 * Carries `waitlistedAt` (DESIGN.md Section 4, Q7 resolved — FIFO waitlist
 * ordering): set only on the transition into WAITLISTED, left unchanged
 * while continuously WAITLISTED, and cleared on leaving WAITLISTED (i.e.
 * whenever the outcome is CONFIRMED or NONE, this must be null). Promotion
 * selects ascending `waitlistedAt` with Invitation id as a deterministic
 * tie-breaker (see AttendanceOutcomeRepository).
 *
 * Deliberately NOT represented here: any explicit waitlist position/rank
 * column — ordering is derived entirely from `waitlistedAt` plus Invitation
 * id, never a separately maintained rank.
 */
@Entity
@Table(
        name = "attendance_outcomes",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_outcome_invitation", columnNames = {"invitation_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_id", nullable = false)
    private Invitation invitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttendanceOutcomeValue outcome;

    /**
     * Set only when this Invitation transitions into WAITLISTED; left
     * unchanged while it remains continuously WAITLISTED; cleared (null)
     * whenever it leaves WAITLISTED (outcome CONFIRMED or NONE). Business
     * logic (RsvpService), not the database, enforces this — there is no
     * database CHECK constraint tying this column's nullability to the
     * outcome value.
     */
    @Column(name = "waitlisted_at")
    private LocalDateTime waitlistedAt;
}
