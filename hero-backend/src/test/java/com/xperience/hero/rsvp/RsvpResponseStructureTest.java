package com.xperience.hero.rsvp;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Structural checks that don't require a Spring context: this slice must
 * keep RSVP Response strictly separate from Attendance Outcome, capacity,
 * and waitlist concepts (DESIGN.md I6) — none of those are implemented yet.
 */
class RsvpResponseStructureTest {

    private static final List<String> FORBIDDEN_SUBSTRINGS = List.of(
            "confirmed", "waitlist", "outcome", "capacity", "promot", "position"
    );

    @Test
    void hasNoAttendanceOutcomeOrCapacityFields() {
        for (Field field : RsvpResponse.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            boolean matchesForbidden = FORBIDDEN_SUBSTRINGS.stream().anyMatch(lowerName::contains);
            assertFalse(matchesForbidden,
                    "RsvpResponse must not contain an Attendance Outcome/capacity field, found: " + field.getName());
        }
    }

    @Test
    void enumContainsOnlyYesNoMaybe() {
        Set<String> names = Arrays.stream(RsvpResponseValue.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of("YES", "NO", "MAYBE"), names);
    }
}
