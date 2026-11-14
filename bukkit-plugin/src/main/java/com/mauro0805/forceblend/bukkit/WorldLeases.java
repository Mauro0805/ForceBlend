package com.mauro0805.forceblend.bukkit;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

// Worker-only: opening/closing another channel in the server JVM can release its POSIX process locks.
final class WorldLeases implements AutoCloseable {
    private final List<Lease> leases = new ArrayList<>();

    static WorldLeases acquire(List<Path> worlds) throws IOException {
        WorldLeases result = new WorldLeases();
        try {
            for (Path world : worlds) {
                Path path = world.resolve("session.lock");
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("An existing regular session.lock is required: " + world);
                }
                // The alias pins identity without relying on optional BasicFileAttributes.fileKey support.
                Path directory = Files.createTempDirectory(world.getParent(), ".forceblend-lease-");
                Path alias = directory.resolve("session.lock");
                FileChannel channel = null;
                try {
                    Files.createLink(alias, path);
                    if (!Files.isRegularFile(alias, LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("Linked session identity is not a regular file");
                    }
                    channel = FileChannel.open(alias, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                    FileLock lock = channel.tryLock();
                    if (lock == null) throw new IOException("World is locked by another process: " + world);
                    result.leases.add(new Lease(path, alias, directory, channel, lock));
                } catch (IOException | RuntimeException exception) {
                    if (channel != null) {
                        try { channel.close(); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                    }
                    try { Files.deleteIfExists(alias); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                    try { Files.delete(directory); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
                    if (exception instanceof OverlappingFileLockException) {
                        throw new IOException("World is already owned by this server: " + world, exception);
                    }
                    throw exception;
                }
            }
            result.validate();
            return result;
        } catch (IOException | RuntimeException exception) {
            try { result.close(); } catch (IOException cleanup) { exception.addSuppressed(cleanup); }
            throw exception;
        }
    }

    void validate() throws IOException {
        for (Lease lease : leases) {
            if (!lease.lock().isValid() || !Files.isRegularFile(lease.path(), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isSameFile(lease.path(), lease.alias())) {
                throw new IOException("World session identity changed: " + lease.path());
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (int index = leases.size() - 1; index >= 0; index--) {
            Lease lease = leases.get(index);
            try {
                lease.channel().close();
            } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
            try {
                Files.deleteIfExists(lease.alias());
                Files.deleteIfExists(lease.directory());
            } catch (IOException exception) {
                if (failure == null) failure = exception; else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    private record Lease(Path path, Path alias, Path directory, FileChannel channel, FileLock lock) {}
}
