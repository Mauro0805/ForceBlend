package com.mauro0805.forceblend.backup;

/**
 * Receives inexpensive, file-level backup progress updates.
 */
@FunctionalInterface
public interface BackupProgressListener {
    BackupProgressListener NONE = progress -> {
    };

    void onProgress(BackupProgress progress);
}
