package com.xperience.hero.invitation;

import com.xperience.hero.event.CreateEventRequest;
import com.xperience.hero.event.EventCreationResult;
import com.xperience.hero.event.EventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only POST /api/events/{eventId}/invitations. No other endpoint
 * exists in this slice.
 *
 * @Transactional wraps each test (and its MockMvc calls, run synchronously
 * in the same thread) in a transaction rolled back afterward.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InvitationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventService eventService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private EventCreationResult createTestEvent() {
        return eventService.createEvent(new CreateEventRequest(
                "Team Offsite", "Quarterly planning session", LocalDateTime.now().plusDays(7), "Main Office", 10
        ));
    }

    @Test
    void validRequestReturns201WithFullBody() throws Exception {
        EventCreationResult event = createTestEvent();

        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \" Guest@Example.com \"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertNotNull(json.get("id"));
        assertEquals(event.event().getId(), json.get("eventId").asLong());
        assertEquals("guest@example.com", json.get("inviteeEmail").asText());
        assertFalse(json.get("invitationToken").asText().isBlank());
    }

    @Test
    void responseDoesNotExposeTokenHashes() throws Exception {
        EventCreationResult event = createTestEvent();

        MvcResult result = mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \"guest@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertFalse(json.has("invitationTokenHash"));
        assertFalse(json.has("hostTokenHash"));
        assertFalse(json.has("tokenHash"));
    }

    @Test
    void missingHostTokenReturns401() throws Exception {
        EventCreationResult event = createTestEvent();

        mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \"guest@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidHostTokenReturns401() throws Exception {
        EventCreationResult event = createTestEvent();

        mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", "not-the-real-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \"guest@example.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonexistentEventReturns404() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/invitations", 999_999_999L)
                        .header("X-Host-Management-Token", "any-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \"guest@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateInvitationReturns409() throws Exception {
        EventCreationResult event = createTestEvent();
        String body = "{\"inviteeEmail\": \"guest@example.com\"}";

        mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void blankEmailReturns400() throws Exception {
        EventCreationResult event = createTestEvent();

        mockMvc.perform(post("/api/events/{eventId}/invitations", event.event().getId())
                        .header("X-Host-Management-Token", event.rawHostManagementToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteeEmail\": \"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
