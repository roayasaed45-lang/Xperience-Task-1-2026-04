package com.xperience.hero.rsvp;

import com.xperience.hero.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RsvpResponseRepository extends JpaRepository<RsvpResponse, Long> {

    /**
     * All RsvpResponse rows for every Invitation on one Event, in a single
     * query — used by the host dashboard (read-only) to avoid an N+1 query
     * per invitation.
     */
    List<RsvpResponse> findByInvitation_Event(Event event);
}
