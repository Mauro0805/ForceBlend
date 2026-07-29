package com.mauro0805.forceblend.processing;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Memory-efficient set of chunk positions grouped by Anvil region.
 */
final class ChunkPositionIndex {
    static final int CHUNKS_PER_REGION_AXIS = 32;
    static final int CHUNKS_PER_REGION = CHUNKS_PER_REGION_AXIS * CHUNKS_PER_REGION_AXIS;

    private final Map<Long, BitSet> chunksByRegion = new HashMap<>();
    private int size;

    private static int localIndex(int chunkX, int chunkZ) {
        return Math.floorMod(chunkX, CHUNKS_PER_REGION_AXIS)
            + Math.floorMod(chunkZ, CHUNKS_PER_REGION_AXIS) * CHUNKS_PER_REGION_AXIS;
    }

    private static long pack(int x, int z) {
        return (long) x << 32 | z & 0xFFFF_FFFFL;
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }

    void add(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION_AXIS);
        int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION_AXIS);
        BitSet chunks = chunksByRegion.computeIfAbsent(pack(regionX, regionZ), ignored -> new BitSet(CHUNKS_PER_REGION));
        int index = localIndex(chunkX, chunkZ);
        if (chunks.get(index)) {
            return;
        }
        chunks.set(index);
        size++;
    }

    boolean contains(int chunkX, int chunkZ) {
        int regionX = Math.floorDiv(chunkX, CHUNKS_PER_REGION_AXIS);
        int regionZ = Math.floorDiv(chunkZ, CHUNKS_PER_REGION_AXIS);
        BitSet chunks = chunksByRegion.get(pack(regionX, regionZ));
        return chunks != null && chunks.get(localIndex(chunkX, chunkZ));
    }

    boolean contains(long chunkX, long chunkZ) {
        return chunkX >= Integer.MIN_VALUE
            && chunkX <= Integer.MAX_VALUE
            && chunkZ >= Integer.MIN_VALUE
            && chunkZ <= Integer.MAX_VALUE
            && contains((int) chunkX, (int) chunkZ);
    }

    int size() {
        return size;
    }

    ChunkPositionIndex selectSquareFrontier(int radius) {
        if (radius < 1) {
            throw new IllegalArgumentException("Frontier radius must be positive");
        }

        ChunkPositionIndex frontier = new ChunkPositionIndex();
        forEach((chunkX, chunkZ) -> {
            if (hasMissingNeighbor(chunkX, chunkZ, radius)) {
                frontier.add(chunkX, chunkZ);
            }
        });
        return frontier;
    }

    void forEach(ChunkConsumer consumer) {
        for (Map.Entry<Long, BitSet> entry : chunksByRegion.entrySet()) {
            int regionX = unpackX(entry.getKey());
            int regionZ = unpackZ(entry.getKey());
            int chunkXOffset = regionX * CHUNKS_PER_REGION_AXIS;
            int chunkZOffset = regionZ * CHUNKS_PER_REGION_AXIS;
            BitSet chunks = entry.getValue();
            for (int index = chunks.nextSetBit(0); index >= 0; index = chunks.nextSetBit(index + 1)) {
                consumer.accept(chunkXOffset + (index & 31), chunkZOffset + (index >>> 5));
            }
        }
    }

    private boolean hasMissingNeighbor(int chunkX, int chunkZ, int radius) {
        for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                if (!contains((long) chunkX + offsetX, (long) chunkZ + offsetZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    interface ChunkConsumer {
        void accept(int chunkX, int chunkZ);
    }
}
