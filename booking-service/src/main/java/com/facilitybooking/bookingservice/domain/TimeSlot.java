package com.facilitybooking.bookingservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Value Object: TimeSlot.
 * Invariants:
 *   INV-TS1: startTime must not be null
 *   INV-TS2: endTime must not be null
 *   INV-TS3: startTime must be before endTime
 *   INV-TS4: startTime must be in the future at creation time
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TimeSlot {

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    public static TimeSlot of(Instant startTime, Instant endTime) {
        if (startTime == null)  throw new IllegalArgumentException("INV-TS1: startTime must not be null");
        if (endTime == null)    throw new IllegalArgumentException("INV-TS2: endTime must not be null");
        if (!startTime.isBefore(endTime)) throw new IllegalArgumentException("INV-TS3: startTime must be before endTime");
        if (!startTime.isAfter(Instant.now())) throw new IllegalArgumentException("INV-TS4: startTime must be in the future");

        TimeSlot ts = new TimeSlot();
        ts.startTime = startTime;
        ts.endTime   = endTime;
        return ts;
    }

    public boolean overlapsWith(TimeSlot other) {
        return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
    }
}
