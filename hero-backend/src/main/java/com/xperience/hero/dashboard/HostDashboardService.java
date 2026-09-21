package com.xperience.hero.dashboard;

import com.xperience.hero.attendance.AttendanceOutcome;
import com.xperience.hero.attendance.AttendanceOutcomeRepository;
import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.event.Event;
import com.xperience.hero.event.EventNotFoundException;
import com.xperience.hero.event.EventService;
import com.xperience.hero.event.InvalidHostTokenException;
import com.xperience.hero.invitation.Invitation;
import com.xperience.hero.invitation.InvitationRepository;
import com.xperience.hero.rsvp.RsvpResponse;
import com.xperience.hero.rsvp.RsvpResponseRepository;
import com.xperience.hero.rsvp.RsvpResponseValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only host dashboard for one Event, gated by the existing host
 * management token (DESIGN.md Q3/I9). Never mutates any state.
 *
 * Counts are derived on every read from Invitation/RsvpResponse/
 * AttendanceOutcome state — never stored (DESIGN.md Section 8).
 *
 * Ambiguity guard: DESIGN.md Section 8 explicitly leaves undecided whether
 * an Invitation may have more than one RsvpResponse or AttendanceOutcome
 * row, and how to pick the "current" one if so. No write path implemented
 * so far can produce more than one row per Invitation, so this is not
 * expected to occur — but if it ever does, this service refuses to guess
 * (see resolveSingle) rather than silently choosing one arbitrarily.
 */
@Service
public class HostDashboardService {

    private final EventService eventService;
    private final InvitationRepository invitationRepository;
    private final RsvpResponseRepository rsvpResponseRepository;
    private final AttendanceOutcomeRepository attendanceOutcomeRepository;

    public HostDashboardService(EventService eventService,
                                 InvitationRepository invitationRepository,
                                 RsvpResponseRepository rsvpResponseRepository,
                                 AttendanceOutcomeRepository attendanceOutcomeRepository) {
        this.eventService = eventService;
        this.invitationRepository = invitationRepository;
        this.rsvpResponseRepository = rsvpResponseRepository;
        this.attendanceOutcomeRepository = attendanceOutcomeRepository;
    }

    @Transactional(readOnly = true)
    public HostDashboardResponse getDashboard(Long eventId, String hostManagementToken) {
        if (hostManagementToken == null || hostManagementToken.isBlank()) {
            throw new InvalidHostTokenException();
        }

        Event event = eventService.getEventById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (!eventService.verifyHostToken(eventId, hostManagementToken)) {
            throw new InvalidHostTokenException();
        }

        List<Invitation> invitations = invitationRepository.findByEvent(event);

        Map<Long, List<RsvpResponse>> responsesByInvitationId = rsvpResponseRepository.findByInvitation_Event(event)
                .stream()
                .collect(Collectors.groupingBy(r -> r.getInvitation().getId()));

        Map<Long, List<AttendanceOutcome>> outcomesByInvitationId = attendanceOutcomeRepository.findByInvitation_Event(event)
                .stream()
                .collect(Collectors.groupingBy(o -> o.getInvitation().getId()));

        List<InvitationDashboardRow> rows = new ArrayList<>();
        int yes = 0;
        int no = 0;
        int maybe = 0;
        int noResponse = 0;
        int confirmed = 0;
        int waitlisted = 0;

        for (Invitation invitation : invitations) {
            RsvpResponseValue currentResponse = resolveSingle(
                    responsesByInvitationId.getOrDefault(invitation.getId(), List.of()),
                    invitation.getId(), "RsvpResponse", RsvpResponse::getResponse);

            AttendanceOutcomeValue currentOutcome = resolveSingle(
                    outcomesByInvitationId.getOrDefault(invitation.getId(), List.of()),
                    invitation.getId(), "AttendanceOutcome", AttendanceOutcome::getOutcome);

            rows.add(new InvitationDashboardRow(
                    invitation.getId(), invitation.getInviteeEmail(), currentResponse, currentOutcome));

            if (currentResponse == null) {
                noResponse++;
            } else {
                switch (currentResponse) {
                    case YES -> yes++;
                    case NO -> no++;
                    case MAYBE -> maybe++;
                }
            }

            if (currentOutcome == AttendanceOutcomeValue.CONFIRMED) {
                confirmed++;
            } else if (currentOutcome == AttendanceOutcomeValue.WAITLISTED) {
                waitlisted++;
            }
        }

        DashboardCounts counts = new DashboardCounts(
                invitations.size(), yes, no, maybe, noResponse, confirmed, waitlisted);

        return new HostDashboardResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventDateTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getStatus(),
                rows,
                counts
        );
    }

    /**
     * Returns null if no row exists, the single value if exactly one row
     * exists, or throws if more than one exists — never picks one
     * arbitrarily (see class-level javadoc and AmbiguousInvitationStateException).
     */
    private <T, V> V resolveSingle(List<T> rows, Long invitationId, String concept, Function<T, V> extractor) {
        if (rows.isEmpty()) {
            return null;
        }
        if (rows.size() > 1) {
            throw new AmbiguousInvitationStateException(invitationId, concept, rows.size());
        }
        return extractor.apply(rows.get(0));
    }
}
