package com.mauro0805.forceblend.backup;

/**
 * Allows a backup operation to be cancelled cooperatively between file reads.
 */
@FunctionalInterface
public interface BackupCancellationToken {
    BackupCancellationToken NEVER_CANCELLED = () -> false;

    boolean isCancellationRequested();
}
