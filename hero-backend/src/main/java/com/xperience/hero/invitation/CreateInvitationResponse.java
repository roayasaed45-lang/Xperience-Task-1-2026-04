package com.xperience.hero.invitation;

/**
 * Response body for POST /api/events/{eventId}/invitations.
 *
 * invitationToken is the RAW token, returned exactly once, here, at
 * creation time. It is never persisted in raw form and no future endpoint
 * returns it again. The stored token hash is intentionally NOT included
 * here or anywhere else in this API.
 */
public record CreateInvitationResponse(
        Long id,
        Long eventId,
        String inviteeEmail,
        String invitationToken
) {
}
