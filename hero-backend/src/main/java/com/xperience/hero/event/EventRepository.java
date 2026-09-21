package com.xperience.hero.event;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Acquires a pessimistic write (row) lock on the Event, implementing
     * DESIGN.md Section 10's settled "First-Pass Concurrency Decision": a
     * per-event pessimistic/serialized correctness boundary around the
     * capacity decision. Concurrent capacity evaluations for the SAME
     * event serialize against this lock; unrelated events are unaffected.
     * Must be called within an active transaction — the lock is held until
     * that transaction ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> lockForCapacityDecision(@Param("id") Long id);
}
