package com.mauro0805.forceblend.processing;

import java.time.Duration;
import java.util.Objects;

/**
 * Optional detailed event for region-level diagnostics.
 */
public record ProcessingRegionEvent(
    ProcessingPhase phase,
    RegionEventState state,
    String regionFile,
    int chunks,
    Duration duration
) {
    public ProcessingRegionEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(regionFile, "regionFile");
        Objects.requireNonNull(duration, "duration");
        if (regionFile.isBlank()) {
            throw new IllegalArgumentException("Region file must not be blank");
        }
        if (chunks < 0 || duration.isNegative()) {
            throw new IllegalArgumentException("Region event totals must not be negative");
        }
    }
}
