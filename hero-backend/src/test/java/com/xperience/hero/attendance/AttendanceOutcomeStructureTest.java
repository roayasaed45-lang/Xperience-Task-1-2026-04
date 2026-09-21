package com.xperience.hero.attendance;

import com.xperience.hero.rsvp.RsvpResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Structural checks for the AttendanceOutcome entity (no Spring context
 * required): it must not reference RsvpResponse — DESIGN.md establishes
 * Attendance Outcome and RSVP Response as sibling concepts under the same
 * Invitation, not parent/child — and it must not contain any capacity,
 * waitlist position, promotion, or ordering/history field.
 */
class AttendanceOutcomeStructureTest {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "response", "capacity", "position", "promot", "count", "history", "rank", "order"
    );

    @Test
    void doesNotReferenceRsvpResponseType() {
        for (Field field : AttendanceOutcome.class.getDeclaredFields()) {
            assertNotEquals(RsvpResponse.class, field.getType(),
                    "AttendanceOutcome must not reference RsvpResponse, found field: " + field.getName());
        }
    }

    @Test
    void hasNoCapacityWaitlistOrPromotionFields() {
        for (Field field : AttendanceOutcome.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            boolean matchesForbidden = FORBIDDEN_SUBSTRINGS.stream().anyMatch(lowerName::contains);
            assertFalse(matchesForbidden,
                    "AttendanceOutcome must not contain a capacity/waitlist/promotion/ordering field, found: " + field.getName());
        }
    }

    @Test
    void hasExactlyIdInvitationAndOutcomeFields() {
        assertEquals(3, AttendanceOutcome.class.getDeclaredFields().length,
                "AttendanceOutcome should have exactly id, invitation, and outcome fields");
    }
}
