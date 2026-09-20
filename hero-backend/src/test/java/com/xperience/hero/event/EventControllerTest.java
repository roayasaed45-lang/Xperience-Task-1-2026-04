package com.xperience.hero.event;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests only POST /api/events. No other endpoint exists in this slice.
 *
 * @Transactional wraps each test method (and the MockMvc call within it,
 * since MockMvc runs synchronously in the same thread) in a transaction that
 * is rolled back afterward — no manual cleanup/TRUNCATE is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createsEventViaEndpoint() throws Exception {
        String body = """
                {
                  "title": "Launch Party",
                  "description": "Product launch",
                  "eventDateTime": "2027-01-01T18:00:00",
                  "location": "HQ",
                  "maxCapacity": 50
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertNotNull(json.get("id"));
        assertEquals("OPEN", json.get("status").asText());
        String rawToken = json.get("hostManagementToken").asText();
        assertFalse(rawToken.isBlank());

        Long eventId = json.get("id").asLong();
        Event persisted = eventRepository.findById(eventId).orElseThrow();
        assertNotEquals(rawToken, persisted.getHostTokenHash());
    }

    @Test
    void responseDoesNotExposeTokenHash() throws Exception {
        String body = """
                {
                  "title": "Launch Party",
                  "eventDateTime": "2027-01-01T18:00:00",
                  "location": "HQ"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

        assertFalse(json.has("hostTokenHash"));
        assertFalse(json.has("tokenHash"));
    }

    @Test
    void clientCannotSupplyStatusTokenOrHash() throws Exception {
        String body = """
                {
                  "title": "Launch Party",
                  "eventDateTime": "2027-01-01T18:00:00",
                  "location": "HQ",
                  "status": "CANCELLED",
                  "hostToken": "client-chosen-token",
                  "hostTokenHash": "client-chosen-hash"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        if (statusCode >= 200 && statusCode < 300) {
            JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
            assertEquals("OPEN", json.get("status").asText());
            assertNotEquals("client-chosen-token", json.get("hostManagementToken").asText());
        } else {
            assertTrue(statusCode >= 400 && statusCode < 500);
        }
    }

    @Test
    void rejectsMissingTitleWithBadRequest() throws Exception {
        String body = """
                {
                  "eventDateTime": "2027-01-01T18:00:00",
                  "location": "HQ"
                }
                """;

        MvcResult result = mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.has("error"));
        assertFalse(json.get("error").asText().isBlank());
        assertEquals("Event title is required", json.get("error").asText());
    }

    @Test
    void rejectsNegativeMaxCapacityWithBadRequest() throws Exception {
        String body = """
                {
                  "title": "Open House",
                  "eventDateTime": "2027-01-01T18:00:00",
                  "location": "Community Hall",
                  "maxCapacity": -1
                }
                """;

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
