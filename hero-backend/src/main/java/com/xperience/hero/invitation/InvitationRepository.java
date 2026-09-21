package com.xperience.hero.invitation;

import com.xperience.hero.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
