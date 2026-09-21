package com.xperience.hero.invitation;

/**
 * Request body for POST /api/events/{eventId}/invitations.
 *
 * Deliberately contains only the client-safe field. Normalization (trim +
 * lowercase) happens server-side in InvitationService, never trusted from
 * the client.
 */
public record CreateInvitationRequest(String inviteeEmail) {
}
