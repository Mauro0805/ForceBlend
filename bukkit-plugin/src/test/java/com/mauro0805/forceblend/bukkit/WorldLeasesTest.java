package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.AccessDeniedException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorldLeasesTest {
    @TempDir Path root;

    @Test
    void excludesAnotherLeaseWithoutChangingLockBytesAndReleasesOnClose() throws IOException {
        Path world = world("world");
        byte[] before = Files.readAllBytes(world.resolve("session.lock"));
        try (WorldLeases lease = WorldLeases.acquire(List.of(world))) {
            lease.validate();
            assertThrows(IOException.class, () -> WorldLeases.acquire(List.of(world)));
        }
        assertArrayEquals(before, Files.readAllBytes(world.resolve("session.lock")));
        try (WorldLeases next = WorldLeases.acquire(List.of(world))) { next.validate(); }
    }

    @Test
    void refusesMinecraftStyleExistingLock() throws IOException {
        Path world = world("world");
        try (FileChannel channel = FileChannel.open(world.resolve("session.lock"), StandardOpenOption.WRITE);
             var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertThrows(IOException.class, () -> WorldLeases.acquire(List.of(world)));
        }
    }

    @Test
    void missingLockIsNotCreatedAndEarlierAcquisitionsAreReleased() throws IOException {
        Path first = world("first");
        Path missing = Files.createDirectory(root.resolve("missing"));
        assertThrows(IOException.class, () -> WorldLeases.acquire(List.of(first, missing)));
        assertFalse(Files.exists(missing.resolve("session.lock")));
        try (WorldLeases next = WorldLeases.acquire(List.of(first))) { next.validate(); }
    }

    private Path world(String name) throws IOException {
        Path world = Files.createDirectory(root.resolve(name));
        Files.writeString(world.resolve("session.lock"), "☃");
        return world;
    }

    @Test
    void replacementCannotSilentlyRetainAValidLease() throws IOException {
        Path world = world("world");
        Path original = world.resolve("session.lock");
        try (WorldLeases lease = WorldLeases.acquire(List.of(world))) {
            try {
                Files.move(original, world.resolve("old.lock"));
            } catch (AccessDeniedException denied) {
                lease.validate();
                assertEquals("☃", Files.readString(original));
                return;
            }
            Files.writeString(original, "replacement");
            assertThrows(IOException.class, lease::validate);
        }
        try (var files = Files.list(root)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".forceblend-lease-")));
        }
    }
}
