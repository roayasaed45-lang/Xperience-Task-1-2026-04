package com.xperience.hero.rsvp;

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
 * RSVP Response domain entity, per DESIGN.md Section 8 (Data Ownership and
 * State Model — "RSVP Response" row) and I6 (RSVP Value).
 *
 * Represents only the invitee's stated choice (Yes/No/Maybe), associated
 * with exactly one Invitation. This is deliberately kept separate from
 * Attendance Outcome (Confirmed/Waitlisted/None) — DESIGN.md I6 explicitly
 * requires this separation, and Section 8 lists Attendance Outcome as its
 * own future concept, derived from Response plus capacity state.
 *
 * Deliberately NOT represented here: any Attendance Outcome, capacity
 * result, waitlist position, or promotion state — all belong to future
 * capacity/waitlist logic, not implemented in this slice.
 *
 * DESIGN.md's current-state persistence model (Section 4/8, Resolved
 * Decision) is now settled: each Invitation has exactly one current
 * RsvpResponse row, enforced by UNIQUE(invitation_id) below. A "change"
 * updates this row in place — RsvpService never inserts a second row for
 * the same Invitation. This is current-state only, not history.
 */
@Entity
@Table(
        name = "rsvp_responses",
        uniqueConstraints = @UniqueConstraint(name = "uk_rsvp_response_invitation", columnNames = {"invitation_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RsvpResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitation_id", nullable = false)
    private Invitation invitation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpResponseValue response;
}
