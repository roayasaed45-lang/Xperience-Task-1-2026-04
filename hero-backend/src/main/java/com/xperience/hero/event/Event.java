package com.xperience.hero.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Event domain entity, per DESIGN.md Section 8 (Data Ownership and State Model).
 *
 * Only the fields explicitly required by the task brief/DESIGN.md are represented.
 * Lifecycle is a single status value (EventStatus), not independent booleans;
 * "start reached" / lock-after-start is intentionally NOT stored here — it remains
 * derived from eventDateTime versus current time (DESIGN.md I2, Section 8).
 *
 * Host ownership (DESIGN.md I8) is represented via hostTokenHash below —
 * DESIGN.md Q3 is resolved as the "Host Management Token" decision: the
 * backend generates a random token at creation and stores only its SHA-256
 * hash here. No User entity, username, email, password, or JWT is used.
 *
 * eventDateTime is stored as LocalDateTime, which carries no timezone
 * information at all. This does not resolve DESIGN.md Q12 (timezone rules) —
 * it simply defers it further; any future comparison of this value against
 * "current time" (e.g., lock-after-start) will still require Q12 to be
 * answered before that comparison is meaningful across timezones.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "event_date_time", nullable = false)
    private LocalDateTime eventDateTime;

    @Column(nullable = false)
    private String location;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    /**
     * SHA-256 hash of the backend-generated host management token
     * (DESIGN.md Q3 — Host Management Token). This is the first-pass host
     * ownership credential for this event.
     *
     * The raw token is NEVER stored here and NEVER logged; only this
     * one-way hash is persisted. This field must never be exposed directly
     * in any API response — CreateEventResponse deliberately omits it, and
     * @JsonIgnore below is a defense-in-depth backstop in case this entity
     * is ever serialized directly instead of through a DTO.
     */
    @JsonIgnore
    @Column(name = "host_token_hash", nullable = false)
    private String hostTokenHash;
}
