package com.xperience.hero.event;

import java.time.LocalDateTime;

/**
 * Request body for POST /api/events.
 *
 * Deliberately contains only client-safe fields. No id, status, host token,
 * or host token hash — those are server-owned values (DESIGN.md Q3) and
 * cannot be supplied by a client through this type.
 */
public record CreateEventRequest(
        String title,
        String description,
        LocalDateTime eventDateTime,
        String location,
        Integer maxCapacity
) {
}
