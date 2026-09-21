package com.xperience.hero.dashboard;

/**
 * Derived-only counts for the host dashboard — never stored in the
 * database (DESIGN.md Section 8: "Dashboard counts | Derived (not stored)").
 * Computed fresh from Invitation/RsvpResponse/AttendanceOutcome each time
 * the dashboard is read.
 */
public record DashboardCounts(
        int totalInvited,
        int yesCount,
        int noCount,
        int maybeCount,
        int noResponseCount,
        int confirmedCount,
        int waitlistedCount
) {
}
