package com.mauro0805.forceblend.processing;

/**
 * Loader-neutral controls for one analysis and application cycle.
 *
 * @param threadCount   worker threads, or {@link #ALL_AVAILABLE_THREADS}
 * @param maximumChunks maximum chunks changed by one application, or {@link #UNLIMITED_CHUNKS}
 */
public record ProcessingOptions(int threadCount, int maximumChunks) {
    public static final int ALL_AVAILABLE_THREADS = 0;
    public static final int UNLIMITED_CHUNKS = -1;
    public static final ProcessingOptions DEFAULT = new ProcessingOptions(
        ALL_AVAILABLE_THREADS,
        UNLIMITED_CHUNKS
    );

    public ProcessingOptions {
        if (threadCount < ALL_AVAILABLE_THREADS) {
            throw new IllegalArgumentException("Thread count must be zero (all available) or positive");
        }
        if (maximumChunks < UNLIMITED_CHUNKS) {
            throw new IllegalArgumentException("Maximum chunks must be -1 (unlimited), zero, or positive");
        }
    }

    public static ProcessingOptions defaults() {
        return DEFAULT;
    }

    public int effectiveThreadCount() {
        return threadCount == ALL_AVAILABLE_THREADS
            ? Math.max(1, Runtime.getRuntime().availableProcessors())
            : threadCount;
    }

    public boolean hasChunkLimit() {
        return maximumChunks != UNLIMITED_CHUNKS;
    }

    int selectedChunkCount(int affectedChunks) {
        return hasChunkLimit() ? Math.min(maximumChunks, affectedChunks) : affectedChunks;
    }
}
