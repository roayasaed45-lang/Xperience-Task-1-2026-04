package com.xperience.hero.rsvp;

/**
 * RSVP submission/change request body.
 *
 * response is deliberately a raw String, not RsvpResponseValue, so
 * RsvpService controls parsing and can reject an invalid value with a
 * clean IllegalArgumentException (-> 400 with a safe message) instead of
 * relying on Jackson's own enum-deserialization failure behavior.
 */
public record RsvpSubmissionRequest(String response) {
}
