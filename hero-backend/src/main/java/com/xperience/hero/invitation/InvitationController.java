package com.xperience.hero.invitation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal Invitation controller.
 *
 * Implements only POST /api/events/{eventId}/invitations (create),
 * authorized via the host management token carried in the
 * X-Host-Management-Token header — this is only the HTTP transport for the
 * already-resolved host credential (DESIGN.md Q3); no Spring Security,
 * JWT, or user accounts are introduced.
 *
 * Deliberately NOT implemented: RSVP endpoints, dashboard, close, cancel,
 * or invitation delivery/email.
 */
@RestController
@RequestMapping("/api/events/{eventId}/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    public ResponseEntity<CreateInvitationResponse> createInvitation(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Host-Management-Token", required = false) String hostManagementToken,
            @RequestBody CreateInvitationRequest request) {

        InvitationCreationResult result = invitationService.createInvitation(
                eventId, hostManagementToken, request.inviteeEmail());
        Invitation invitation = result.invitation();

        CreateInvitationResponse response = new CreateInvitationResponse(
                invitation.getId(),
                invitation.getEvent().getId(),
                invitation.getInviteeEmail(),
                result.rawInvitationToken()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
