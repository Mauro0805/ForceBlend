package com.mauro0805.forceblend.processing;

/**
 * Describes how ForceBlend handled one chunk.
 */
public enum ChunkModification {
    PREPARED(true),
    REPAIRED_UNSAFE_MARKER(true),
    ALREADY_PREPARED(false),
    SKIPPED_INCOMPLETE(false),
    SKIPPED_NOT_SELECTED(false),
    SKIPPED_NATIVE_MIGRATION(false);

    private final boolean changed;

    ChunkModification(boolean changed) {
        this.changed = changed;
    }

    public boolean changed() {
        return changed;
    }
}
