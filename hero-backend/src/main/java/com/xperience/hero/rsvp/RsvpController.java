package com.xperience.hero.rsvp;

import com.xperience.hero.invitation.Invitation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Invitee-facing RSVP submission/change endpoint, authorized via the
 * invitation token carried in the X-Invitation-Token header — the HTTP
 * transport for the already-resolved invitee credential (DESIGN.md Q2); no
 * Spring Security, JWT, or user accounts are introduced.
 *
 * Deliberately NOT implemented: waitlist promotion, event lifecycle
 * close/cancel endpoints, or invitation delivery/email.
 */
@RestController
@RequestMapping("/api/invitations/rsvp")
public class RsvpController {

    private final RsvpService rsvpService;

    public RsvpController(RsvpService rsvpService) {
        this.rsvpService = rsvpService;
    }

    @PutMapping
    public ResponseEntity<RsvpStateResponse> submitOrChangeRsvp(
            @RequestHeader(value = "X-Invitation-Token", required = false) String invitationToken,
            @RequestBody RsvpSubmissionRequest request) {

        RsvpSubmissionResult result = rsvpService.submitOrChangeRsvp(invitationToken, request.response());
        Invitation invitation = result.invitation();

        RsvpStateResponse response = new RsvpStateResponse(
                invitation.getId(),
                invitation.getEvent().getId(),
                result.response(),
                result.outcome()
        );

        return ResponseEntity.ok(response);
    }
}
