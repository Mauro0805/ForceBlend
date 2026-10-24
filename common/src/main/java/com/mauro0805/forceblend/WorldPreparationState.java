package com.mauro0805.forceblend;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class WorldPreparationState {
    public static final String MARKER_FILE = "forceblend.json";
    public static final String LEGACY_MARKER_FILE = "forceblendonce.json";

    private WorldPreparationState() {
    }

    public static boolean isComplete(Path worldDirectory) throws IOException {
        return hasMarker(worldDirectory.resolve(MARKER_FILE))
            || hasMarker(worldDirectory.resolve(LEGACY_MARKER_FILE));
    }

    private static boolean hasMarker(Path marker) throws IOException {
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Unsafe ForceBlend completion marker: " + marker);
        }
        return true;
    }

    public static void markComplete(Path worldDirectory) throws IOException {
        if (!Files.isDirectory(worldDirectory, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(worldDirectory)) {
            throw new IOException("Cannot record completion outside a regular world directory: " + worldDirectory);
        }
        Path marker = worldDirectory.resolve(MARKER_FILE);
        if (hasMarker(marker)) {
            return;
        }
        Path temporary = Files.createTempFile(worldDirectory, ".forceblend-state-", ".tmp");
        try {
            byte[] json = "{\n  \"schemaVersion\": 1,\n  \"completed\": true\n}\n"
                .getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(json);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (hasMarker(marker)) {
                return;
            }
            try {
                Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, marker);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
