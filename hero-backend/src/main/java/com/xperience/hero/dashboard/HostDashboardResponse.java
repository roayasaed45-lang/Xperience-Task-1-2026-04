package com.xperience.hero.dashboard;

import com.xperience.hero.event.EventStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response body for GET /api/events/{eventId}/dashboard.
 *
 * Contains Event details, one row per Invitation, and derived-only counts.
 * Never includes the invitation token hash, host token hash, or any raw
 * token — none of those fields exist anywhere in this type or its nested
 * records.
 */
public record HostDashboardResponse(
        Long eventId,
        String title,
        String description,
        LocalDateTime eventDateTime,
        String location,
        Integer maxCapacity,
        EventStatus status,
        List<InvitationDashboardRow> invitations,
        DashboardCounts counts
) {
}
