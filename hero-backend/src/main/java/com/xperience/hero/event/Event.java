package com.xperience.hero.event;

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
 * Host ownership (DESIGN.md I8) is INTENTIONALLY NOT represented on this entity.
 * Host authentication/identity (DESIGN.md Q3) is unresolved, and no minimal
 * representation can be added without implicitly choosing an identity model.
 * I8 therefore cannot yet be fully satisfied — see EventService for the
 * corresponding deferral of event creation.
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
}
