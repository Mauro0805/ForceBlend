package com.mauro0805.forceblend.client.ui;

import java.time.Duration;

public record WorldAnalysisView(
    int affectedChunks,
    int selectedChunks,
    int deferredChunks,
    int affectedRegions,
    int scannedChunks,
    Duration estimatedDuration
) {
    public WorldAnalysisView {
        if (affectedChunks < 0 || selectedChunks < 0 || deferredChunks < 0
            || affectedRegions < 0 || scannedChunks < 0) {
            throw new IllegalArgumentException("World analysis values cannot be negative");
        }
        estimatedDuration = estimatedDuration == null ? Duration.ZERO : estimatedDuration;
    }
}
