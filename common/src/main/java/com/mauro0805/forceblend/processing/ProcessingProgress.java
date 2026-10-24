package com.mauro0805.forceblend.processing;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot suitable for a progress screen or detailed logging.
 */
public record ProcessingProgress(
    ProcessingPhase phase,
    int completedRegions,
    int totalRegions,
    int completedChunks,
    int totalChunks,
    int affectedChunks,
    List<String> activeRegions,
    Duration elapsed
) {
    public static final int UNKNOWN_TOTAL = -1;

    public ProcessingProgress {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(activeRegions, "activeRegions");
        Objects.requireNonNull(elapsed, "elapsed");
        if (completedRegions < 0 || totalRegions < 0 || completedRegions > totalRegions) {
            throw new IllegalArgumentException("Invalid region progress");
        }
        if (completedChunks < 0 || totalChunks < UNKNOWN_TOTAL) {
            throw new IllegalArgumentException("Invalid chunk progress");
        }
        if (totalChunks != UNKNOWN_TOTAL && completedChunks > totalChunks) {
            throw new IllegalArgumentException("Completed chunks exceed the known total");
        }
        if (affectedChunks < 0) {
            throw new IllegalArgumentException("Affected chunks must not be negative");
        }
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
        activeRegions = List.copyOf(activeRegions);
    }

    public double completionFraction() {
        if (totalChunks > 0) {
            return (double) completedChunks / totalChunks;
        }
        if (totalRegions > 0) {
            return (double) completedRegions / totalRegions;
        }
        return phase == ProcessingPhase.ANALYSIS_COMPLETE || phase == ProcessingPhase.COMPLETE ? 1.0 : 0.0;
    }

    /**
     * Convenience accessor for compact UIs. Parallel callers can use {@link #activeRegions()} for the full set.
     */
    public Optional<String> currentRegion() {
        return activeRegions.isEmpty() ? Optional.empty() : Optional.of(activeRegions.get(0));
    }

    /**
     * Computes a rolling ETA from elapsed time and completed work. Empty means that no useful sample exists yet.
     */
    public Optional<Duration> estimatedRemaining() {
        long completedWork;
        long totalWork;
        if (totalChunks > 0) {
            completedWork = completedChunks;
            totalWork = totalChunks;
        } else if (totalRegions > 0) {
            completedWork = completedRegions;
            totalWork = totalRegions;
        } else if (phase == ProcessingPhase.ANALYSIS_COMPLETE || phase == ProcessingPhase.COMPLETE) {
            return Optional.of(Duration.ZERO);
        } else {
            return Optional.empty();
        }

        if (completedWork == 0) {
            return Optional.empty();
        }
        if (completedWork >= totalWork) {
            return Optional.of(Duration.ZERO);
        }

        double remainingNanos = (double) elapsed.toNanos()
            * (totalWork - completedWork)
            / completedWork;
        long boundedNanos = remainingNanos >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, (long) remainingNanos);
        return Optional.of(Duration.ofNanos(boundedNanos));
    }
}
