package com.xperience.hero.event;

/**
 * Internal service-layer carrier for the result of creating an Event: the
 * persisted Event plus the raw host management token.
 *
 * This is NOT a REST DTO. The raw token exists only transiently here — it is
 * never persisted (only its hash is, on Event.hostTokenHash) and cannot be
 * reconstructed after this point.
 */
public record EventCreationResult(Event event, String rawHostManagementToken) {
}
