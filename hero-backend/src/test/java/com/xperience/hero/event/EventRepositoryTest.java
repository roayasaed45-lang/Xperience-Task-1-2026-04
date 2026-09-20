package com.xperience.hero.event;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests only the persistence foundation implemented in this slice: entity
 * structure, repository round-trips, and lifecycle status representation.
 *
 * Deliberately does NOT test: host ownership (Q3 unresolved, not represented
 * on the entity), any HTTP endpoint (none exists), or any feature not yet
 * implemented (creation workflow, capacity, waitlist, invitations, RSVP).
 */
@SpringBootTest
@Transactional
class EventRepositoryTest {

    @Autowired
    private EventRepository eventRepository;

    private Event.EventBuilder baseEvent() {
        return Event.builder()
                .title("Team Offsite")
                .description("Quarterly planning session")
                .eventDateTime(LocalDateTime.now().plusDays(7))
                .location("Main Office")
                .status(EventStatus.OPEN);
    }

    @Test
    void persistsAndRetrievesEvent() {
        Event saved = eventRepository.save(
                baseEvent().maxCapacity(10).build()
        );

        assertNotNull(saved.getId());

        Optional<Event> found = eventRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("Team Offsite", found.get().getTitle());
        assertEquals("Quarterly planning session", found.get().getDescription());
        assertEquals("Main Office", found.get().getLocation());
        assertEquals(10, found.get().getMaxCapacity());
        assertEquals(EventStatus.OPEN, found.get().getStatus());
    }

    @Test
    void allowsNullMaxCapacity() {
        Event saved = eventRepository.save(
                baseEvent().maxCapacity(null).build()
        );

        Optional<Event> found = eventRepository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertNull(found.get().getMaxCapacity());
    }

    @Test
    void persistsEachLifecycleStatusValue() {
        for (EventStatus status : EventStatus.values()) {
            Event saved = eventRepository.save(
                    baseEvent().maxCapacity(5).status(status).build()
            );

            Optional<Event> found = eventRepository.findById(saved.getId());
            assertTrue(found.isPresent());
            assertEquals(status, found.get().getStatus());
        }
    }
}
