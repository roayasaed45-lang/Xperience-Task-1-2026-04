package com.xperience.hero.event;

import java.time.LocalDateTime;

/**
 * Safe Event summary returned by the Close/Cancel lifecycle endpoints.
 * Deliberately excludes hostTokenHash and every other internal field.
 */
public record EventLifecycleResponse(
        Long id,
        EventStatus status,
        String title,
        LocalDateTime eventDateTime) {
}
