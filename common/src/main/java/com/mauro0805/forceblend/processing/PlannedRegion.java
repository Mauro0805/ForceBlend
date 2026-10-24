package com.mauro0805.forceblend.processing;

import java.nio.file.Path;
import java.util.BitSet;
import java.util.Objects;

final class PlannedRegion {
    private final Path regionFile;
    private final int regionX;
    private final int regionZ;
    private final RegionFileSnapshot snapshot;
    private final int discoveredChunks;
    private final BitSet frontierChunks;
    private final BitSet candidateChunks;
    private final BitSet selectedChunks;
    private final ChunkModification[] modifications;

    PlannedRegion(
        Path regionFile,
        int regionX,
        int regionZ,
        RegionFileSnapshot snapshot,
        int discoveredChunks,
        BitSet frontierChunks,
        BitSet candidateChunks,
        BitSet selectedChunks,
        ChunkModification[] modifications
    ) {
        this.regionFile = Objects.requireNonNull(regionFile, "regionFile");
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.discoveredChunks = discoveredChunks;
        this.frontierChunks = (BitSet) frontierChunks.clone();
        this.candidateChunks = (BitSet) candidateChunks.clone();
        this.selectedChunks = (BitSet) selectedChunks.clone();
        this.modifications = modifications.clone();
    }

    Path regionFile() {
        return regionFile;
    }

    int regionX() {
        return regionX;
    }

    int regionZ() {
        return regionZ;
    }

    RegionFileSnapshot snapshot() {
        return snapshot;
    }

    int discoveredChunks() {
        return discoveredChunks;
    }

    int selectedChunkCount() {
        return selectedChunks.cardinality();
    }

    boolean isSelected(int index) {
        return selectedChunks.get(index);
    }

    boolean isCandidate(int index) {
        return candidateChunks.get(index);
    }

    BitSet presentChunks() {
        BitSet present = new BitSet(ChunkPositionIndex.CHUNKS_PER_REGION);
        for (int index = 0; index < modifications.length; index++) {
            if (modifications[index] != null) {
                present.set(index);
            }
        }
        return present;
    }

    ChunkModification modification(int index) {
        return modifications[index];
    }
}
