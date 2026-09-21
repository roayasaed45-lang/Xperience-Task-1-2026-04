package com.xperience.hero.invitation;

import com.xperience.hero.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * Best-effort duplicate pre-check for the resolved Q8/I13 policy — an
     * Event must not have more than one Invitation for the same normalized
     * invitee email. This is NOT the authoritative enforcement; the
     * UNIQUE(event_id, invitee_email) database constraint on Invitation is.
     * The caller is responsible for passing an already-normalized email.
     */
    Optional<Invitation> findByEventAndInviteeEmail(Event event, String inviteeEmail);

    /** All Invitations for one Event — used by the host dashboard (read-only). */
    List<Invitation> findByEvent(Event event);

    /**
     * Resolves the Invitation for a caller-presented invitation token
     * (DESIGN.md Q2 — resolved). The caller must hash the raw token via
     * InvitationTokenService before calling this; the raw token itself is
     * never looked up directly and never persisted.
     */
    Optional<Invitation> findByInvitationTokenHash(String invitationTokenHash);
}
