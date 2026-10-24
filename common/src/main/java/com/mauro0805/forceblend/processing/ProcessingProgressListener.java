package com.mauro0805.forceblend.processing;

/**
 * Receives serialized, observational progress updates from the processing core.
 */
@FunctionalInterface
public interface ProcessingProgressListener {
    ProcessingProgressListener NO_OP = progress -> {
    };

    void onProgress(ProcessingProgress progress);

    /**
     * Opts into potentially high-volume region and chunk diagnostics.
     */
    default boolean detailedEventsEnabled() {
        return false;
    }

    default void onRegionEvent(ProcessingRegionEvent event) {
    }

    default void onChunkEvent(ChunkProcessingEvent event) {
    }
}
