package com.mauro0805.forceblend.client.ui;

import java.time.Duration;

public record ProcessingProgressView(
    String phase,
    int completedRegions,
    int totalRegions,
    int completedChunks,
    int totalChunks,
    int affectedChunks,
    String currentRegion,
    Duration elapsed,
    Duration estimatedRemaining
) {
    public ProcessingProgressView {
        phase = phase == null || phase.isBlank() ? "Preparing world" : phase;
        currentRegion = currentRegion == null || currentRegion.isBlank() ? "Waiting..." : currentRegion;
        elapsed = elapsed == null ? Duration.ZERO : elapsed;
        estimatedRemaining = estimatedRemaining == null ? Duration.ZERO : estimatedRemaining;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(value, 1.0));
    }

    public double fraction() {
        if (totalChunks > 0) {
            return clamp((double) completedChunks / totalChunks);
        }
        if (totalRegions > 0) {
            return clamp((double) completedRegions / totalRegions);
        }
        return 0.0;
    }

    public double chunksPerSecond() {
        double seconds = elapsed.toNanos() / 1_000_000_000.0;
        return seconds > 0.0 ? completedChunks / seconds : 0.0;
    }
}
