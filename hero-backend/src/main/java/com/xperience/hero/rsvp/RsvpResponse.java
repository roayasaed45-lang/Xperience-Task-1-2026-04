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
 * Relationship note: the association below is a plain @ManyToOne with no
 * uniqueness constraint on invitation_id. Whether an Invitation may
 * legitimately have more than one RsvpResponse row, or whether a "change"
 * must update an existing row in place, is a service-layer/workflow
 * decision — deferred along with the rest of RSVP submission/change, since
 * building that workflow now would require resolving duplicate-request
 * behavior, I2 lock-after-start enforcement, and invitee scoping (I10/Q2),
 * none of which are settled. This entity's shape does not decide any of
 * that; it only records that a given response value belongs to a given
 * invitation.
 */
@Entity
@Table(name = "rsvp_responses")
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
