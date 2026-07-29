package com.mauro0805.forceblend.processing;

/**
 * Exact read-only analysis totals plus work-size inputs for an initial ETA.
 */
public record ProcessingPlanSummary(
    int regionFiles,
    int discoveredChunks,
    int frontierChunks,
    int affectedChunks,
    int selectedChunks,
    int deferredChunks,
    int affectedRegions,
    int selectedRegions,
    long analyzedRegionBytes,
    long selectedRegionBytes
) {
    public ProcessingPlanSummary {
        if (regionFiles < 0 || discoveredChunks < 0 || frontierChunks < 0 || affectedChunks < 0
            || selectedChunks < 0 || deferredChunks < 0 || affectedRegions < 0 || selectedRegions < 0
            || analyzedRegionBytes < 0 || selectedRegionBytes < 0) {
            throw new IllegalArgumentException("Processing plan totals must not be negative");
        }
        if (selectedChunks > affectedChunks || deferredChunks != affectedChunks - selectedChunks) {
            throw new IllegalArgumentException("Selected and deferred chunk totals are inconsistent");
        }
        if (selectedRegions > affectedRegions || affectedRegions > regionFiles) {
            throw new IllegalArgumentException("Region selection totals are inconsistent");
        }
        if (selectedRegionBytes > analyzedRegionBytes) {
            throw new IllegalArgumentException("Selected region bytes exceed analyzed bytes");
        }
    }
}
