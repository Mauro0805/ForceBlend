package com.mauro0805.forceblend.backup;

import java.util.Objects;

/**
 * A completed-file snapshot emitted while a world backup is being written.
 */
public record BackupProgress(
    long totalFiles,
    long totalBytes,
    long completedFiles,
    long completedBytes,
    String currentEntry
) {
    public BackupProgress {
        if (totalFiles < 0 || totalBytes < 0 || completedFiles < 0 || completedBytes < 0) {
            throw new IllegalArgumentException("Backup progress counters cannot be negative");
        }
        if (completedFiles > totalFiles || completedBytes > totalBytes) {
            throw new IllegalArgumentException("Completed backup work cannot exceed the known totals");
        }
        Objects.requireNonNull(currentEntry, "currentEntry");
    }
}
