package com.xperience.hero.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Minimal REST error handling — not a general error-handling framework.
 *
 * Currently handles only IllegalArgumentException, which is how basic field
 * validation failures (e.g., EventService.validate) are currently signaled,
 * so that invalid input returns a clean 400 Bad Request instead of
 * propagating as an unhandled exception. The exception's message is already
 * a safe, human-readable string (e.g., "Event title is required") — no
 * stack trace or internal detail is ever included in the response.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }
}
