package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WorldLeaseProcessTest {
    @TempDir Path world;

    @Test
    void twoRejectedProbesCannotReleaseTheParentJvmLock() throws IOException {
        Path path = world.resolve("session.lock");
        Files.writeString(path, "☃");
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertTrue(lock.isValid());
            assertThrows(IOException.class, () -> WorldLeaseProcess.acquire(List.of(world)));
            assertThrows(IOException.class, () -> WorldLeaseProcess.acquire(List.of(world)));
        }
        assertEquals("☃", Files.readString(path));
        try (WorldLeaseProcess lease = WorldLeaseProcess.acquire(List.of(world))) { lease.validate(); }
    }

    @Test
    void helperExcludesAnotherProcessThenReleasesNormally() throws IOException {
        Files.writeString(world.resolve("session.lock"), "☃");
        try (WorldLeaseProcess first = WorldLeaseProcess.acquire(List.of(world))) {
            first.validate();
            assertThrows(IOException.class, () -> WorldLeaseProcess.acquire(List.of(world)));
        }
        try (WorldLeaseProcess next = WorldLeaseProcess.acquire(List.of(world))) { next.validate(); }
    }
}
