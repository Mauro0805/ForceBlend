package com.mauro0805.forceblend.processing;

import java.time.Duration;
import java.util.Objects;

/**
 * Optional detailed event for one analyzed or applied chunk.
 * A changed analysis outcome with {@code selected == false} was deferred by the configured limit.
 */
public record ChunkProcessingEvent(
    ProcessingPhase phase,
    String regionFile,
    int chunkX,
    int chunkZ,
    ChunkModification outcome,
    boolean selected,
    Duration elapsed
) {
    public ChunkProcessingEvent {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(regionFile, "regionFile");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(elapsed, "elapsed");
        if (regionFile.isBlank()) {
            throw new IllegalArgumentException("Region file must not be blank");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
    }
}
