package com.xperience.hero.dashboard;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal, read-only Host Dashboard controller.
 *
 * Implements only GET /api/events/{eventId}/dashboard, authorized via the
 * same X-Host-Management-Token header used for invitation creation — the
 * HTTP transport for the already-resolved host credential (DESIGN.md Q3).
 * No Spring Security, JWT, or user accounts.
 */
@RestController
@RequestMapping("/api/events/{eventId}/dashboard")
public class HostDashboardController {

    private final HostDashboardService hostDashboardService;

    public HostDashboardController(HostDashboardService hostDashboardService) {
        this.hostDashboardService = hostDashboardService;
    }

    @GetMapping
    public ResponseEntity<HostDashboardResponse> getDashboard(
            @PathVariable Long eventId,
            @RequestHeader(value = "X-Host-Management-Token", required = false) String hostManagementToken) {

        HostDashboardResponse response = hostDashboardService.getDashboard(eventId, hostManagementToken);
        return ResponseEntity.ok(response);
    }
}
