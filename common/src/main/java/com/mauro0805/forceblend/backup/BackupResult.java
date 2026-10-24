package com.mauro0805.forceblend.backup;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Summary of a completed, durable world backup.
 */
public record BackupResult(Path archive, long files, long uncompressedBytes) {
    public BackupResult {
        archive = Objects.requireNonNull(archive, "archive").toAbsolutePath().normalize();
        if (files < 0 || uncompressedBytes < 0) {
            throw new IllegalArgumentException("Backup result counters cannot be negative");
        }
    }
}
