package com.mauro0805.forceblend.processing;

public record ProcessingReport(
    int regionFiles,
    int discoveredChunks,
    int frontierChunks,
    int preparedChunks,
    int repairedChunks,
    int skippedChunks,
    int alreadyPreparedChunks
) {
    public static final ProcessingReport EMPTY = new ProcessingReport(0, 0, 0, 0, 0, 0, 0);

    public ProcessingReport add(ProcessingReport other) {
        return new ProcessingReport(
            regionFiles + other.regionFiles,
            discoveredChunks + other.discoveredChunks,
            frontierChunks + other.frontierChunks,
            preparedChunks + other.preparedChunks,
            repairedChunks + other.repairedChunks,
            skippedChunks + other.skippedChunks,
            alreadyPreparedChunks + other.alreadyPreparedChunks
        );
    }
}
