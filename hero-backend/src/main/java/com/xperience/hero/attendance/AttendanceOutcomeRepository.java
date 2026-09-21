package com.xperience.hero.attendance;

import com.xperience.hero.event.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceOutcomeRepository extends JpaRepository<AttendanceOutcome, Long> {

    /**
     * All AttendanceOutcome rows for every Invitation on one Event, in a
     * single query — used by the host dashboard (read-only) to avoid an
     * N+1 query per invitation.
     */
    List<AttendanceOutcome> findByInvitation_Event(Event event);
}
