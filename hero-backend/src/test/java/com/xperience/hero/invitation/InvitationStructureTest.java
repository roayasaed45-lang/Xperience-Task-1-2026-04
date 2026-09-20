package com.xperience.hero.invitation;

import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventStatus;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural checks that don't require a Spring context: this slice must not
 * introduce RSVP/Attendance Outcome fields on Invitation, and the invitation
 * token hash must never appear in serialized JSON.
 */
class InvitationStructureTest {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "response", "outcome", "confirmed", "waitlist"
    );

    @Test
    void hasNoRsvpOrAttendanceOutcomeFields() {
        for (Field field : Invitation.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            boolean matchesForbidden = FORBIDDEN_SUBSTRINGS.stream().anyMatch(lowerName::contains);
            assertFalse(matchesForbidden,
                    "Invitation must not contain an RSVP/Attendance Outcome field, found: " + field.getName());
        }
    }

    @Test
    void tokenHashIsNotSerializedToJson() {
        Event event = Event.builder()
                .id(1L)
                .title("Team Offsite")
                .eventDateTime(LocalDateTime.now().plusDays(7))
                .location("Main Office")
                .status(EventStatus.OPEN)
                .hostTokenHash("event-host-hash-should-not-appear-either")
                .build();

        Invitation invitation = Invitation.builder()
                .id(1L)
                .event(event)
                .inviteeEmail("guest@example.com")
                .invitationTokenHash("secret-invitation-hash-value")
                .build();

        String json = new ObjectMapper().writeValueAsString(invitation);

        assertFalse(json.contains("invitationTokenHash"));
        assertFalse(json.contains("secret-invitation-hash-value"));
        assertFalse(json.contains("hostTokenHash"));
        assertFalse(json.contains("event-host-hash-should-not-appear-either"));
        assertTrue(json.contains("guest@example.com"));
    }
}
