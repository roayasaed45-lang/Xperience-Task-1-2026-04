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
 * Deliberately NOT represented here: capacity counters, waitlist position,
 * promotion timestamps, or any ordering/history field — all of that
 * requires waitlist/promotion logic and depends on unresolved questions
 * (Q7, Q13), not decided in this slice.
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
}
