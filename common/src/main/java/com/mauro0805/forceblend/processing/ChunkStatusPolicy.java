package com.mauro0805.forceblend.processing;

import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.StringTag;
import net.querz.nbt.tag.Tag;

/**
 * Restricts forced blending to complete chunks that contain stable player-visible terrain.
 */
final class ChunkStatusPolicy {
    private static final String STATUS_KEY = "Status";
    private static final String FULL_STATUS = "minecraft:full";

    private ChunkStatusPolicy() {
    }

    static boolean isEligibleForBlending(CompoundTag chunkTag) {
        Tag<?> rawStatus = chunkTag.get(STATUS_KEY);
        if (!(rawStatus instanceof StringTag statusTag) || statusTag.getValue().isBlank()) {
            return false;
        }
        return FULL_STATUS.equals(ensureNamespaced(statusTag.getValue()));
    }

    private static String ensureNamespaced(String status) {
        return status.indexOf(':') < 0 ? "minecraft:" + status : status;
    }
}
