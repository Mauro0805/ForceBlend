package com.mauro0805.forceblend.processing;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

final class RegionFileSnapshot {
    private final long size;
    private final long lastModifiedMillis;
    private final byte[] headerDigest;
    private final Map<Integer, byte[]> externalChunkDigests;

    RegionFileSnapshot(
        long size,
        long lastModifiedMillis,
        byte[] headerDigest,
        Map<Integer, byte[]> externalChunkDigests
    ) {
        this.size = size;
        this.lastModifiedMillis = lastModifiedMillis;
        this.headerDigest = headerDigest.clone();
        Map<Integer, byte[]> copiedDigests = new TreeMap<>();
        externalChunkDigests.forEach((index, digest) -> copiedDigests.put(index, digest.clone()));
        this.externalChunkDigests = Map.copyOf(copiedDigests);
    }

    long size() {
        return size;
    }

    long lastModifiedMillis() {
        return lastModifiedMillis;
    }

    boolean headerMatches(byte[] digest) {
        return Arrays.equals(headerDigest, digest);
    }

    Map<Integer, byte[]> externalChunkDigests() {
        return externalChunkDigests;
    }
}
