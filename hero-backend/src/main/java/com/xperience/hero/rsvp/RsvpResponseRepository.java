package com.xperience.hero.rsvp;

import com.xperience.hero.event.Event;
import com.xperience.hero.invitation.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RsvpResponseRepository extends JpaRepository<RsvpResponse, Long> {

    /**
     * All RsvpResponse rows for every Invitation on one Event, in a single
     * query — used by the host dashboard (read-only) to avoid an N+1 query
     * per invitation.
     */
    List<RsvpResponse> findByInvitation_Event(Event event);

    /**
     * The current RsvpResponse row(s) for one Invitation. Expected to be at
     * most one (UNIQUE(invitation_id) enforces this at the DB level) — a
     * List, not an Optional, so RsvpService can explicitly detect and
     * refuse to silently resolve the (should-be-impossible) ambiguous case,
     * the same defensive pattern used by the host dashboard.
     */
    List<RsvpResponse> findByInvitation(Invitation invitation);
}
