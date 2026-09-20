package com.xperience.hero.event;

import java.time.LocalDateTime;

/**
 * Response body for POST /api/events.
 *
 * hostManagementToken is the RAW token, returned exactly once, here, at
 * creation time (DESIGN.md Q3). It is never persisted in raw form and no
 * future endpoint returns it again — the caller must retain it. There is no
 * recovery mechanism in this first-pass model.
 *
 * The stored host token hash is intentionally NOT included here or anywhere
 * else in this API.
 */
public record CreateEventResponse(
        Long id,
        String title,
        String description,
        LocalDateTime eventDateTime,
        String location,
        Integer maxCapacity,
        EventStatus status,
        String hostManagementToken
) {
}
