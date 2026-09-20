package com.xperience.hero.event;

/**
 * Single lifecycle status representation for an Event, per DESIGN.md Section 6/13.
 * Start/lock state is intentionally NOT represented here — it remains derived
 * from the event's date/time rather than stored (DESIGN.md Section 8).
 *
 * The distinction in behavior between CLOSED and CANCELLED is unresolved
 * (DESIGN.md Q11) and is not decided by this enum.
 */
public enum EventStatus {
    OPEN,
    CLOSED,
    CANCELLED
}
