package com.xperience.hero.invitation;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Invitation business logic — persistence-read foundation only.
 *
 * Invitation creation is deliberately NOT implemented here. Any
 * general-purpose "create an invitation" operation must, at least by
 * omission, take a stance on whether repeat invitations to the same email
 * for the same event are allowed, rejected, or merged — and DESIGN.md Q8
 * (are duplicate invitations allowed?) is unresolved. A prior version of
 * this service exposed createInvitation(eventId, inviteeEmail), which
 * always inserted a new row regardless of existing invitations to the same
 * email — that is itself an implicit "duplicates allowed" stance, not a
 * neutral default. Rather than silently keep that stance, no creation
 * operation is exposed until Q8 is resolved.
 *
 * Also deliberately NOT implemented: invitation delivery, invitee
 * authentication (Q2, unresolved), token expiry (Q9, unresolved), RSVP,
 * capacity/waitlist.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;

    public InvitationService(InvitationRepository invitationRepository) {
        this.invitationRepository = invitationRepository;
    }

    public Optional<Invitation> getInvitationById(Long id) {
        return invitationRepository.findById(id);
    }
}
