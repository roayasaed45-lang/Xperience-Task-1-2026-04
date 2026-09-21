package com.xperience.hero.rsvp;

import com.xperience.hero.attendance.AttendanceOutcomeValue;
import com.xperience.hero.invitation.Invitation;

/** Internal result of RsvpService.submitOrChangeRsvp, for the controller to shape into RsvpStateResponse. */
public record RsvpSubmissionResult(
        Invitation invitation,
        RsvpResponseValue response,
        AttendanceOutcomeValue outcome) {
}
