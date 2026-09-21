package com.xperience.hero.rsvp;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import com.xperience.hero.invitation.InvitationCreationResult;
import com.xperience.hero.invitation.InvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only PUT /api/invitations/rsvp. No other endpoint exists in this
 * slice. Follows the same MockMvc + @Transactional-rollback pattern as
 * InvitationControllerTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RsvpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventService eventService;

    @Autowired
    private InvitationService invitationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventCreationResult createTestEvent() {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10));
    }

    private InvitationCreationResult createTestInvitation(EventCreationResult event) {
        return invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");
    }

    @Test
    void validRequestReturns200WithCurrentState() throws Exception {
        EventCreationResult event = createTestEvent();
        InvitationCreationResult invitation = createTestInvitation(event);

        MvcResult result = mockMvc.perform(put("/api/invitations/rsvp")
                        .header("X-Invitation-Token", invitation.rawInvitationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"YES\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertEquals(invitation.invitation().getId(), json.get("invitationId").asLong());
        assertEquals(event.event().getId(), json.get("eventId").asLong());
        assertEquals("YES", json.get("response").asText());
        assertEquals("CONFIRMED", json.get("attendanceOutcome").asText());
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/invitations/rsvp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"YES\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(put("/api/invitations/rsvp")
                        .header("X-Invitation-Token", "not-a-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"YES\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidResponseValueReturns400() throws Exception {
        EventCreationResult event = createTestEvent();
        InvitationCreationResult invitation = createTestInvitation(event);

        mockMvc.perform(put("/api/invitations/rsvp")
                        .header("X-Invitation-Token", invitation.rawInvitationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"NOT_A_REAL_VALUE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rsvpAfterEventStartReturns409() throws Exception {
        EventCreationResult event = eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().minusMinutes(1), "Main Office", 10));
        InvitationCreationResult invitation = createTestInvitation(event);

        mockMvc.perform(put("/api/invitations/rsvp")
                        .header("X-Invitation-Token", invitation.rawInvitationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"YES\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void responseDoesNotExposeTokenOrHashes() throws Exception {
        EventCreationResult event = createTestEvent();
        InvitationCreationResult invitation = createTestInvitation(event);

        MvcResult result = mockMvc.perform(put("/api/invitations/rsvp")
                        .header("X-Invitation-Token", invitation.rawInvitationToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\": \"YES\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertFalse(json.has("invitationToken"));
        assertFalse(json.has("invitationTokenHash"));
        assertFalse(json.has("hostTokenHash"));
    }
}
