package com.xperience.hero.web;

import com.xperience.hero.dashboard.AmbiguousInvitationStateException;
import com.xperience.hero.event.EventNotFoundException;
import com.xperience.hero.event.InvalidEventTransitionException;
import com.xperience.hero.event.InvalidHostTokenException;
import com.xperience.hero.invitation.DuplicateInvitationException;
import com.xperience.hero.invitation.InvalidInvitationTokenException;
import com.xperience.hero.rsvp.EventCancelledException;
import com.xperience.hero.rsvp.EventClosedException;
import com.xperience.hero.rsvp.RsvpLockedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Minimal REST error handling — not a general error-handling framework.
 *
 * Each handler returns only a safe, human-readable message (e.g., "Event
 * title is required," "Invalid or missing host management token") — never
 * a stack trace, a database constraint name, a token, or a hash.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidHostTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidHostToken(InvalidHostTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEventNotFound(EventNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(DuplicateInvitationException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateInvitation(DuplicateInvitationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    /**
     * A genuine server-side data-state ambiguity (DESIGN.md Section 8 —
     * not decided), not a client error — mapped to 500 with a safe message
     * rather than letting a raw exception/stack trace escape.
     */
    @ExceptionHandler(AmbiguousInvitationStateException.class)
    public ResponseEntity<ErrorResponse> handleAmbiguousInvitationState(AmbiguousInvitationStateException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidInvitationTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInvitationToken(InvalidInvitationTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RsvpLockedException.class)
    public ResponseEntity<ErrorResponse> handleRsvpLocked(RsvpLockedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EventClosedException.class)
    public ResponseEntity<ErrorResponse> handleEventClosed(EventClosedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EventCancelledException.class)
    public ResponseEntity<ErrorResponse> handleEventCancelled(EventCancelledException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidEventTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEventTransition(InvalidEventTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
