package com.xperience.hero.attendance;

import com.xperience.hero.rsvp.RsvpResponseValue;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural checks only — no Spring context, no persistence, since no
 * Attendance Outcome entity exists in this slice (see the slice report for
 * why the Invitation-vs-RsvpResponse relationship was left undecided).
 *
 * Proves: the enum contains exactly CONFIRMED/WAITLISTED/NONE, contains none
 * of the RSVP Response values, and that AttendanceOutcomeValue and
 * RsvpResponseValue share no member names — the two concepts remain
 * structurally disjoint (DESIGN.md I6).
 */
class AttendanceOutcomeValueTest {

    @Test
    void containsExactlyConfirmedWaitlistedNone() {
        Set<String> names = Arrays.stream(AttendanceOutcomeValue.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertEquals(Set.of("CONFIRMED", "WAITLISTED", "NONE"), names);
    }

    @Test
    void doesNotContainRsvpResponseValues() {
        Set<String> outcomeNames = Arrays.stream(AttendanceOutcomeValue.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(Collections.disjoint(outcomeNames, Set.of("YES", "NO", "MAYBE")));
    }

    @Test
    void isDisjointFromRsvpResponseValueEnum() {
        Set<String> outcomeNames = Arrays.stream(AttendanceOutcomeValue.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        Set<String> responseNames = Arrays.stream(RsvpResponseValue.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertTrue(Collections.disjoint(outcomeNames, responseNames));
    }
}
