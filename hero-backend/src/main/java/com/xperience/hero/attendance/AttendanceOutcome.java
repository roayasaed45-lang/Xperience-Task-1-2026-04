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
 * Relationship note: the association below is a plain @ManyToOne with no
 * uniqueness constraint on invitation_id, mirroring RsvpResponse. DESIGN.md
 * explicitly leaves open whether "one current Attendance Outcome per
 * Invitation" should be enforced as a database uniqueness constraint, or how
 * updates/history are represented at the schema level — that is not decided
 * here.
 *
 * Deliberately NOT represented here: capacity counters, waitlist position,
 * promotion timestamps, or any ordering/history field — all of that
 * requires capacity/waitlist logic and depends on unresolved questions
 * (Q1, Q7, Q13), none of which are decided in this slice.
 */
@Entity
@Table(name = "attendance_outcomes")
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
