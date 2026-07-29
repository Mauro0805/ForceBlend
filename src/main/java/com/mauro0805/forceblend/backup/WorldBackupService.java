package com.mauro0805.forceblend.backup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a complete world ZIP without following links or exposing partial archives.
 */
public final class WorldBackupService {
    private static final int COPY_BUFFER_SIZE = 64 * 1024;
    private static final int MAX_BACKUP_NAME_CODE_POINTS = 80;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern(
        "uuuu-MM-dd_HH-mm-ss",
        Locale.ROOT
    );
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final Clock clock;

    public WorldBackupService() {
        this(Clock.systemDefaultZone());
    }

    public WorldBackupService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static Path defaultBackupRoot(Path gameDirectory) {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        return gameDirectory.toAbsolutePath().normalize().resolve("backups").resolve("forceblend");
    }

    private static void validateSource(Path source) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IOException("A symbolic link cannot be used as the world directory: " + source);
        }
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The selected world directory does not exist or is not a directory: " + source);
        }
    }

    private static SourceManifest collectSourceEntries(
        Path source,
        BackupCancellationToken cancellationToken
    ) throws IOException {
        List<SourceEntry> entries = new ArrayList<>();
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                checkCancellation(cancellationToken);
                if (Files.isSymbolicLink(directory) || attributes.isSymbolicLink()) {
                    throw new IOException("World backups do not follow symbolic-link directories: " + directory);
                }
                if (!directory.equals(source)) {
                    // Directory timestamps may change as files are inspected on some filesystems. The entry set,
                    // file sizes, and file timestamps are the stable signals needed to detect a changed world.
                    entries.add(new SourceEntry(directory, true, 0L, 0));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                checkCancellation(cancellationToken);
                if (Files.isSymbolicLink(file) || attributes.isSymbolicLink()) {
                    throw new IOException("World backups do not include symbolic links: " + file);
                }
                if (!attributes.isRegularFile()) {
                    throw new IOException("World backups only support regular files and directories: " + file);
                }
                entries.add(new SourceEntry(
                    file,
                    false,
                    attributes.lastModifiedTime().toMillis(),
                    attributes.size()
                ));
                return FileVisitResult.CONTINUE;
            }
        });
        entries.sort(Comparator.comparing(entry -> portableRelativePath(source, entry.path())));
        long totalFiles = 0;
        long totalBytes = 0;
        try {
            for (SourceEntry entry : entries) {
                if (!entry.directory()) {
                    totalFiles = Math.addExact(totalFiles, 1);
                    totalBytes = Math.addExact(totalBytes, entry.size());
                }
            }
        } catch (ArithmeticException exception) {
            throw new IOException("The world is too large to represent backup progress safely", exception);
        }
        return new SourceManifest(List.copyOf(entries), totalFiles, totalBytes);
    }

    private static void prepareBackupDirectory(Path backupRoot, Path worldBackupDirectory) throws IOException {
        ensureRealDirectory(backupRoot, "backup root");
        ensureRealDirectory(worldBackupDirectory, "world backup directory");
    }

    private static void ensureRealDirectory(Path directory, String description) throws IOException {
        rejectSymbolicPathComponents(directory, description);
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The " + description + " is not a directory: " + directory);
            }
            return;
        }
        Files.createDirectories(directory);
        rejectSymbolicPathComponents(directory, description);
    }

    private static void rejectSymbolicPathComponents(Path path, String description) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path current = absolutePath.getRoot();
        if (current == null) {
            throw new IOException("The " + description + " must be an absolute path: " + path);
        }
        for (Path component : absolutePath) {
            current = current.resolve(component);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IOException(
                    "A symbolic-link path component cannot be used for the " + description + ": " + current
                );
            }
        }
    }

    private static BackupCounters writeArchive(
        Path source,
        String safeWorldName,
        SourceManifest manifest,
        Path target,
        BackupCancellationToken cancellationToken,
        BackupProgressListener progressListener
    ) throws IOException {
        long completedFiles = 0;
        long completedBytes = 0;
        byte[] buffer = new byte[COPY_BUFFER_SIZE];

        try (ZipOutputStream output = new ZipOutputStream(
            new BufferedOutputStream(Files.newOutputStream(target, StandardOpenOption.TRUNCATE_EXISTING))
        )) {
            output.setLevel(Deflater.BEST_SPEED);
            output.putNextEntry(new ZipEntry(safeWorldName + "/"));
            output.closeEntry();

            for (SourceEntry sourceEntry : manifest.entries()) {
                checkCancellation(cancellationToken);
                String relativePath = portableRelativePath(source, sourceEntry.path());
                String archivePath = safeWorldName + "/" + relativePath + (sourceEntry.directory() ? "/" : "");
                validateArchivePath(archivePath, safeWorldName);

                ZipEntry zipEntry = new ZipEntry(archivePath);
                zipEntry.setTime(sourceEntry.lastModifiedMillis());
                output.putNextEntry(zipEntry);
                if (!sourceEntry.directory()) {
                    if (Files.isSymbolicLink(sourceEntry.path())
                        || !Files.isRegularFile(sourceEntry.path(), LinkOption.NOFOLLOW_LINKS)) {
                        throw new IOException("A world file changed type while it was being backed up: " + sourceEntry.path());
                    }
                    try (InputStream input = new BufferedInputStream(Files.newInputStream(sourceEntry.path()))) {
                        int read;
                        while ((read = input.read(buffer)) != -1) {
                            checkCancellation(cancellationToken);
                            output.write(buffer, 0, read);
                            completedBytes = Math.addExact(completedBytes, read);
                        }
                    } catch (ArithmeticException exception) {
                        throw new IOException("The world backup exceeded the supported byte count", exception);
                    }
                    completedFiles++;
                    progressListener.onProgress(new BackupProgress(
                        manifest.totalFiles(),
                        manifest.totalBytes(),
                        completedFiles,
                        completedBytes,
                        relativePath
                    ));
                }
                output.closeEntry();
            }
        }
        return new BackupCounters(completedFiles, completedBytes);
    }

    private static Path moveToAvailableName(Path source, Path directory, String timestamp) throws IOException {
        for (int sequence = 1; sequence < Integer.MAX_VALUE; sequence++) {
            String suffix = sequence == 1 ? "" : "_" + sequence;
            Path candidate = directory.resolve(timestamp + suffix + ".zip").normalize();
            if (!candidate.startsWith(directory)) {
                throw new IOException("The generated backup path escapes its destination directory");
            }
            boolean reserved = false;
            try {
                Files.createFile(candidate);
                reserved = true;
                moveAtomicallyReplacingReservation(source, candidate);
                reserved = false;
                return candidate;
            } catch (FileAlreadyExistsException ignored) {
                // A backup from the same second already exists; retain both using a numeric suffix.
            } finally {
                if (reserved) {
                    Files.deleteIfExists(candidate);
                }
            }
        }
        throw new IOException("Could not allocate a unique world backup filename");
    }

    private static void moveAtomicallyReplacingReservation(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void forceFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static String sanitizeWorldName(String requestedName, Path source) {
        String fallback = source.getFileName() == null ? "world" : source.getFileName().toString();
        String candidate = requestedName == null || requestedName.isBlank() ? fallback : requestedName.trim();
        StringBuilder sanitized = new StringBuilder(candidate.length());
        candidate.codePoints().limit(MAX_BACKUP_NAME_CODE_POINTS).forEach(codePoint -> {
            boolean invalid = Character.isISOControl(codePoint)
                || codePoint == '<'
                || codePoint == '>'
                || codePoint == ':'
                || codePoint == '"'
                || codePoint == '/'
                || codePoint == '\\'
                || codePoint == '|'
                || codePoint == '?'
                || codePoint == '*';
            sanitized.appendCodePoint(invalid ? '_' : codePoint);
        });

        String result = sanitized.toString().strip();
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isBlank() || result.equals(".") || result.equals("..")) {
            result = "world";
        }
        String deviceName = result;
        int extensionIndex = deviceName.indexOf('.');
        if (extensionIndex >= 0) {
            deviceName = deviceName.substring(0, extensionIndex);
        }
        if (WINDOWS_RESERVED_NAMES.contains(deviceName.toUpperCase(Locale.ROOT))) {
            result = "_" + result;
        }
        return result;
    }

    private static String portableRelativePath(Path source, Path entry) {
        Path relative = source.relativize(entry).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("World entry resolves outside the source directory: " + entry);
        }
        return relative.toString().replace('\\', '/');
    }

    private static void validateArchivePath(String archivePath, String safeWorldName) throws IOException {
        String expectedPrefix = safeWorldName + "/";
        if (archivePath.startsWith("/")
            || archivePath.startsWith("\\")
            || archivePath.contains("\\")
            || !archivePath.startsWith(expectedPrefix)) {
            throw new IOException("Unsafe path rejected while creating the world backup: " + archivePath);
        }
        String relativePath = archivePath.substring(expectedPrefix.length());
        for (String component : relativePath.split("/")) {
            if (component.equals(".") || component.equals("..")) {
                throw new IOException("Unsafe path rejected while creating the world backup: " + archivePath);
            }
        }
    }

    private static void checkCancellation(BackupCancellationToken cancellationToken)
        throws BackupCancelledException {
        if (cancellationToken.isCancellationRequested()) {
            throw new BackupCancelledException();
        }
    }

    public BackupResult createBackup(Path worldDirectory, Path backupRoot, String worldName) throws IOException {
        return createBackup(
            worldDirectory,
            backupRoot,
            worldName,
            BackupCancellationToken.NEVER_CANCELLED,
            BackupProgressListener.NONE
        );
    }

    public BackupResult createBackup(
        Path worldDirectory,
        Path backupRoot,
        String worldName,
        BackupCancellationToken cancellationToken,
        BackupProgressListener progressListener
    ) throws IOException {
        Objects.requireNonNull(worldDirectory, "worldDirectory");
        Objects.requireNonNull(backupRoot, "backupRoot");
        Objects.requireNonNull(cancellationToken, "cancellationToken");
        Objects.requireNonNull(progressListener, "progressListener");

        Path source = worldDirectory.toAbsolutePath().normalize();
        Path normalizedBackupRoot = backupRoot.toAbsolutePath().normalize();
        validateSource(source);
        if (normalizedBackupRoot.startsWith(source) || source.startsWith(normalizedBackupRoot)) {
            throw new IOException("The backup directory must not overlap the world directory");
        }

        String safeWorldName = sanitizeWorldName(worldName, source);
        Path worldBackupDirectory = normalizedBackupRoot.resolve(safeWorldName).normalize();
        if (!worldBackupDirectory.startsWith(normalizedBackupRoot)) {
            throw new IOException("The world backup directory resolves outside the configured backup root");
        }

        SourceManifest manifest = collectSourceEntries(source, cancellationToken);
        checkCancellation(cancellationToken);
        prepareBackupDirectory(normalizedBackupRoot, worldBackupDirectory);

        String timestamp = TIMESTAMP_FORMAT.withZone(clock.getZone()).format(clock.instant());
        Path temporaryArchive = Files.createTempFile(worldBackupDirectory, "." + timestamp + "-", ".zip.part");
        try {
            BackupCounters counters = writeArchive(
                source,
                safeWorldName,
                manifest,
                temporaryArchive,
                cancellationToken,
                progressListener
            );
            checkCancellation(cancellationToken);
            SourceManifest finalManifest = collectSourceEntries(source, cancellationToken);
            if (!manifest.equals(finalManifest)) {
                throw new IOException("The world changed while its backup was being created; no archive was kept");
            }
            forceFile(temporaryArchive);
            Path completedArchive = moveToAvailableName(temporaryArchive, worldBackupDirectory, timestamp);
            return new BackupResult(completedArchive, counters.files(), counters.bytes());
        } finally {
            Files.deleteIfExists(temporaryArchive);
        }
    }

    private record SourceEntry(Path path, boolean directory, long lastModifiedMillis, long size) {
    }

    private record SourceManifest(List<SourceEntry> entries, long totalFiles, long totalBytes) {
    }

    private record BackupCounters(long files, long bytes) {
    }
}
