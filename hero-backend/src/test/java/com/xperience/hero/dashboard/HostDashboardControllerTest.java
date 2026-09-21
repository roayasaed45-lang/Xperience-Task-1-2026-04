package com.xperience.hero.dashboard;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import com.xperience.hero.invitation.InvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only GET /api/events/{eventId}/dashboard. No other dashboard
 * endpoint exists in this slice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HostDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventService eventService;

    @Autowired
    private InvitationService invitationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventCreationResult createTestEvent() {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
    }

    @Test
    void validRequestReturns200WithDashboard() throws Exception {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        MvcResult result = mockMvc.perform(get("/api/events/{eventId}/dashboard", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(event.event().getId(), json.get("eventId").asLong());
        assertEquals(1, json.get("invitations").size());
        assertEquals(1, json.get("counts").get("totalInvited").asInt());
    }

    @Test
    void missingHostTokenReturns401() throws Exception {
        EventCreationResult event = createTestEvent();

        mockMvc.perform(get("/api/events/{eventId}/dashboard", event.event().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidHostTokenReturns401() throws Exception {
        EventCreationResult event = createTestEvent();

        mockMvc.perform(get("/api/events/{eventId}/dashboard", event.event().getId())
                        .header("X-Host-Management-Token", "not-the-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingEventReturns404() throws Exception {
        mockMvc.perform(get("/api/events/{eventId}/dashboard", 999_999_999L)
                        .header("X-Host-Management-Token", "any-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void responseDoesNotExposeSecrets() throws Exception {
        EventCreationResult event = createTestEvent();
        invitationService.createInvitation(event.event().getId(), event.rawHostManagementToken(), "guest@example.com");

        MvcResult result = mockMvc.perform(get("/api/events/{eventId}/dashboard", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken()))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String lowerBody = body.toLowerCase();
        assertFalse(lowerBody.contains("hash"));
        assertFalse(lowerBody.contains("token"));
        assertFalse(body.contains(event.rawHostManagementToken()));
    }

    @Test
    void emptyEventReturnsZeroCounts() throws Exception {
        EventCreationResult event = createTestEvent();

        MvcResult result = mockMvc.perform(get("/api/events/{eventId}/dashboard", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken()))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.get("invitations").isEmpty());
        assertEquals(0, json.get("counts").get("totalInvited").asInt());
    }
}
