package com.xperience.hero.invitation;

import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventNotFoundException;
import com.xperience.hero.event.EventService;
import com.xperience.hero.event.InvalidHostTokenException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Invitation business logic.
 *
 * Implements invitation creation now that its previously-blocking design
 * questions are resolved: host authorization (DESIGN.md Q3 — Host
 * Management Token) and the duplicate-invitation policy plus its
 * concurrency-safe enforcement mechanism (Q8 / I13 — UNIQUE(event_id,
 * invitee_email) database constraint).
 *
 * Deliberately NOT implemented here: invitation delivery, invitee
 * authentication (Q2, unresolved), token expiry (Q9, unresolved), RSVP,
 * capacity/waitlist.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final InvitationTokenService invitationTokenService;
    private final EventService eventService;

    public InvitationService(InvitationRepository invitationRepository,
                              InvitationTokenService invitationTokenService,
                              EventService eventService) {
        this.invitationRepository = invitationRepository;
        this.invitationTokenService = invitationTokenService;
        this.eventService = eventService;
    }

    public Optional<Invitation> getInvitationById(Long id) {
        return invitationRepository.findById(id);
    }

    /**
     * Creates an Invitation for an existing Event, authorized by the
     * event's host management token, and generates its invitation token in
     * one transactional operation (DESIGN.md Section 10 Transaction
     * Boundaries item 4).
     *
     * Duplicate handling (Q8/I13): a best-effort pre-check gives a fast,
     * clear rejection in the common case, but the database's
     * UNIQUE(event_id, invitee_email) constraint is the authoritative
     * guard — if two concurrent requests both pass the pre-check, only one
     * insert succeeds, and the loser's constraint violation is translated
     * into DuplicateInvitationException here, never exposed as a raw
     * database exception.
     *
     * @throws IllegalArgumentException     if eventId or inviteeEmail is missing/blank
     * @throws InvalidHostTokenException    if the host token is missing or does not authorize this event
     * @throws EventNotFoundException       if no Event exists with the given id
     * @throws DuplicateInvitationException if an Invitation already exists for the normalized email on this Event
     */
    @Transactional
    public InvitationCreationResult createInvitation(Long eventId, String hostManagementToken, String inviteeEmail) {
        if (eventId == null) {
            throw new IllegalArgumentException("Event id is required");
        }
        if (hostManagementToken == null || hostManagementToken.isBlank()) {
            throw new InvalidHostTokenException();
        }
        if (inviteeEmail == null) {
            throw new IllegalArgumentException("Invitee email is required");
        }

        Event event = eventService.getEventById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!eventService.verifyHostToken(eventId, hostManagementToken)) {
            throw new InvalidHostTokenException();
        }

        String normalizedEmail = inviteeEmail.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            throw new IllegalArgumentException("Invitee email is required");
        }

        // Best-effort pre-check only — NOT the authoritative protection.
        if (invitationRepository.findByEventAndInviteeEmail(event, normalizedEmail).isPresent()) {
            throw duplicateInvitationException(normalizedEmail);
        }

        String rawToken = invitationTokenService.generateRawToken();
        String tokenHash = invitationTokenService.hashToken(rawToken);

        Invitation invitation = Invitation.builder()
                .event(event)
                .inviteeEmail(normalizedEmail)
                .invitationTokenHash(tokenHash)
                .build();

        Invitation saved;
        try {
            // saveAndFlush forces the INSERT (and the UNIQUE constraint
            // check) to happen now, so a concurrent-duplicate violation can
            // be caught here rather than escaping at a later flush point.
            saved = invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
            throw duplicateInvitationException(normalizedEmail);
        }

        return new InvitationCreationResult(saved, rawToken);
    }

    private DuplicateInvitationException duplicateInvitationException(String normalizedEmail) {
        return new DuplicateInvitationException(
                "An invitation already exists for " + normalizedEmail + " on this event");
    }
}
