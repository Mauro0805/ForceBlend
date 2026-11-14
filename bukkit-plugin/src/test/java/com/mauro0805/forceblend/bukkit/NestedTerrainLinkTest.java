package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NestedTerrainLinkTest {
    @Test
    void rejectsLinkedPreferredTerrainEvenWhenRootRegionIsValid(@TempDir Path container) throws IOException {
        Path world = Files.createDirectories(container.resolve("world/region")).getParent();
        Files.writeString(world.resolve("level.dat"), "directory fixture");
        Path nested = Files.createDirectories(world.resolve("dimensions/minecraft/overworld"));
        Path outside = Files.createDirectory(container.resolve("outside-region"));
        try { Files.createSymbolicLink(nested.resolve("region"), outside); }
        catch (IOException | UnsupportedOperationException exception) {
            assumeTrue(false, "Host cannot create symbolic-link fixture: " + exception);
        }
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world")));
    }
}
