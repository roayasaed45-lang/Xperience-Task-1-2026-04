package com.xperience.hero.invitation;

/**
 * Internal service-layer carrier for the result of creating an Invitation:
 * the persisted Invitation plus the raw invitation token.
 *
 * Not a REST DTO. The raw token exists only transiently here — it is never
 * persisted (only its hash is, on Invitation.invitationTokenHash) and
 * cannot be reconstructed after this point.
 */
public record InvitationCreationResult(Invitation invitation, String rawInvitationToken) {
}
