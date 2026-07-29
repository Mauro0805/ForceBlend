package com.mauro0805.forceblend.config;

/**
 * User-configurable ForceBlend behavior that does not depend on a specific mod loader.
 *
 * @param automaticProcessingEnabled whether read-only analysis starts without a separate pre-analysis prompt
 * @param automaticBackupEnabled     whether a backup is required before each processing run
 * @param detailedLoggingEnabled     whether per-region diagnostics should be logged
 * @param maximumChunksPerRun        maximum chunks to modify in one run, or {@link #UNLIMITED_CHUNKS}
 * @param processingThreads          requested worker count, or {@link #ALL_AVAILABLE_THREADS}
 */
public record ForceBlendConfig(
    boolean automaticProcessingEnabled,
    boolean automaticBackupEnabled,
    boolean detailedLoggingEnabled,
    long maximumChunksPerRun,
    int processingThreads
) {
    public static final long UNLIMITED_CHUNKS = 0L;
    public static final int ALL_AVAILABLE_THREADS = 0;

    /**
     * Prevents a corrupt or hand-edited explicit value from creating an unreasonable executor.
     */
    public static final int MAX_CONFIGURED_THREADS = 256;

    public static final ForceBlendConfig DEFAULTS = new ForceBlendConfig(
        true,
        false,
        false,
        UNLIMITED_CHUNKS,
        ALL_AVAILABLE_THREADS
    );

    public ForceBlendConfig {
        if (maximumChunksPerRun < UNLIMITED_CHUNKS) {
            throw new IllegalArgumentException("Maximum chunks per run cannot be negative");
        }
        if (processingThreads < ALL_AVAILABLE_THREADS || processingThreads > MAX_CONFIGURED_THREADS) {
            throw new IllegalArgumentException(
                "Processing threads must be 'all' or between 1 and " + MAX_CONFIGURED_THREADS
            );
        }
    }

    public boolean hasChunkLimit() {
        return maximumChunksPerRun != UNLIMITED_CHUNKS;
    }

    public boolean usesAllAvailableThreads() {
        return processingThreads == ALL_AVAILABLE_THREADS;
    }

    /**
     * Resolves the configured worker count without ever oversubscribing the processors available
     * to the current JVM.
     */
    public int resolvedProcessingThreads(int availableProcessors) {
        if (availableProcessors < 1) {
            throw new IllegalArgumentException("Available processors must be at least one");
        }

        return usesAllAvailableThreads()
            ? availableProcessors
            : Math.min(processingThreads, availableProcessors);
    }
}
