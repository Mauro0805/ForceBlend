package com.mauro0805.forceblend.backup;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

final class WorldBackupServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-29T21:32:15Z"),
        ZoneOffset.UTC
    );

    @Test
    void createsACompleteZipAndKeepsTimestampCollisions(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("saves").resolve("Proof World"));
        byte[] levelData = "level data".getBytes(StandardCharsets.UTF_8);
        byte[] regionData = {1, 2, 3, 4, 5};
        Files.write(world.resolve("level.dat"), levelData);
        Path regionDirectory = Files.createDirectories(world.resolve("region"));
        Files.write(regionDirectory.resolve("r.0.0.mca"), regionData);
        Files.createDirectories(world.resolve("empty-directory"));
        Path backupRoot = directory.resolve("backups").resolve("forceblend");
        WorldBackupService service = new WorldBackupService(FIXED_CLOCK);

        BackupResult first = service.createBackup(world, backupRoot, "Proof World");
        BackupResult second = service.createBackup(world, backupRoot, "Proof World");

        assertEquals("2026-07-29_21-32-15.zip", first.archive().getFileName().toString());
        assertEquals("2026-07-29_21-32-15_2.zip", second.archive().getFileName().toString());
        assertEquals(2, first.files());
        assertEquals(levelData.length + regionData.length, first.uncompressedBytes());
        try (ZipFile archive = new ZipFile(first.archive().toFile())) {
            Set<String> names = archive.stream().map(entry -> entry.getName()).collect(Collectors.toSet());
            assertEquals(
                Set.of(
                    "Proof World/",
                    "Proof World/empty-directory/",
                    "Proof World/level.dat",
                    "Proof World/region/",
                    "Proof World/region/r.0.0.mca"
                ),
                names
            );
            assertArrayEquals(levelData, archive.getInputStream(archive.getEntry("Proof World/level.dat")).readAllBytes());
            assertArrayEquals(
                regionData,
                archive.getInputStream(archive.getEntry("Proof World/region/r.0.0.mca")).readAllBytes()
            );
        }
    }

    @Test
    void sanitizesARequestedNameWithoutAllowingPathTraversal(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("level.dat"), "data", StandardCharsets.UTF_8);
        Path backupRoot = directory.resolve("backups");

        BackupResult result = new WorldBackupService(FIXED_CLOCK).createBackup(
            world,
            backupRoot,
            "../Unsafe/World"
        );

        assertTrue(result.archive().startsWith(backupRoot.toAbsolutePath().normalize()));
        assertEquals(".._Unsafe_World", result.archive().getParent().getFileName().toString());
        try (ZipFile archive = new ZipFile(result.archive().toFile())) {
            assertTrue(archive.stream().allMatch(entry -> entry.getName().startsWith(".._Unsafe_World/")));
            assertTrue(archive.stream().noneMatch(entry -> entry.getName().contains("../")));
        }
    }

    @Test
    void omitsOnlyTheRootRuntimeLock(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("session.lock"), "runtime lock", StandardCharsets.UTF_8);
        Path data = Files.createDirectories(world.resolve("data"));
        Files.writeString(data.resolve("session.lock"), "world data", StandardCharsets.UTF_8);
        BackupResult result = new WorldBackupService(FIXED_CLOCK).createBackup(
            world,
            directory.resolve("backups"),
            "world"
        );
        try (ZipFile archive = new ZipFile(result.archive().toFile())) {
            assertNull(archive.getEntry("world/session.lock"));
            assertNotNull(archive.getEntry("world/data/session.lock"));
        }
    }

    @Test
    void cancellationRemovesThePartialArchive(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("a.dat"), "first", StandardCharsets.UTF_8);
        Files.writeString(world.resolve("b.dat"), "second", StandardCharsets.UTF_8);
        Path backupRoot = directory.resolve("backups");
        AtomicBoolean cancelled = new AtomicBoolean();

        assertThrows(
            BackupCancelledException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                backupRoot,
                "world",
                cancelled::get,
                progress -> cancelled.set(true)
            )
        );

        Path worldBackups = backupRoot.resolve("world");
        assertTrue(Files.isDirectory(worldBackups));
        try (var files = Files.list(worldBackups)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void anIoFailureRemovesThePartialArchive(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("a.dat"), "first", StandardCharsets.UTF_8);
        Path disappearingFile = world.resolve("b.dat");
        Files.writeString(disappearingFile, "second", StandardCharsets.UTF_8);
        Path backupRoot = directory.resolve("backups");

        assertThrows(
            IOException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                backupRoot,
                "world",
                BackupCancellationToken.NEVER_CANCELLED,
                progress -> {
                    if (progress.currentEntry().equals("a.dat")) {
                        try {
                            Files.delete(disappearingFile);
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    }
                }
            )
        );

        try (var files = Files.list(backupRoot.resolve("world"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void discardsABackupWhenTheWorldChangesBeforePublication(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("level.dat"), "initial", StandardCharsets.UTF_8);
        Path backupRoot = directory.resolve("backups");

        IOException exception = assertThrows(
            IOException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                backupRoot,
                "world",
                BackupCancellationToken.NEVER_CANCELLED,
                progress -> {
                    try {
                        Files.writeString(world.resolve("late.dat"), "late", StandardCharsets.UTF_8);
                    } catch (IOException writeFailure) {
                        throw new IllegalStateException(writeFailure);
                    }
                }
            )
        );

        assertTrue(exception.getMessage().contains("world changed"));
        try (var files = Files.list(backupRoot.resolve("world"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void rejectsBackupLocationsThatOverlapTheWorld(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));

        IOException exception = assertThrows(
            IOException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                world.resolve("backups"),
                "world"
            )
        );

        assertTrue(exception.getMessage().contains("must not overlap"));
        assertFalse(Files.exists(world.resolve("backups")));
    }

    @Test
    void rejectsSymbolicLinksInsteadOfFollowingThem(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Path externalFile = directory.resolve("outside.dat");
        Files.writeString(externalFile, "outside", StandardCharsets.UTF_8);
        Path symbolicLink = world.resolve("linked.dat");
        try {
            Files.createSymbolicLink(symbolicLink, externalFile);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment");
        }

        IOException exception = assertThrows(
            IOException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                directory.resolve("backups"),
                "world"
            )
        );

        assertTrue(exception.getMessage().contains("symbolic link"));
        assertFalse(Files.exists(directory.resolve("backups")));
    }

    @Test
    void rejectsASymbolicLinkInTheBackupPathAncestry(@TempDir Path directory) throws IOException {
        Path world = Files.createDirectories(directory.resolve("world"));
        Files.writeString(world.resolve("level.dat"), "data", StandardCharsets.UTF_8);
        Path realBackupParent = Files.createDirectories(directory.resolve("real-backups"));
        Path linkedBackupParent = directory.resolve("backups");
        try {
            Files.createSymbolicLink(linkedBackupParent, realBackupParent);
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment");
        }

        IOException exception = assertThrows(
            IOException.class,
            () -> new WorldBackupService(FIXED_CLOCK).createBackup(
                world,
                linkedBackupParent.resolve("forceblend"),
                "world"
            )
        );

        assertTrue(exception.getMessage().contains("symbolic-link path component"));
        assertFalse(Files.exists(realBackupParent.resolve("forceblend")));
    }
}
