package com.mauro0805.forceblend.backup;

import java.io.IOException;

/**
 * Signals a user-requested cancellation after all temporary backup files were cleaned up.
 */
public final class BackupCancelledException extends IOException {
    private static final long serialVersionUID = 1L;

    public BackupCancelledException() {
        super("World backup was cancelled");
    }
}
