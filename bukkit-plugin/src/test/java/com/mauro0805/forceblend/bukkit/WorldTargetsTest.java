package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldTargetsTest {
    @TempDir Path container;

    @Test
    void acceptsExistingTargetWithoutChangingPayload() throws IOException {
        Path world = createWorld("world");
        assertEquals(List.of(world.toRealPath()), WorldTargets.validate(container, List.of("world")));
        assertEquals("fixture", Files.readString(world.resolve("level.dat")));
    }

    @Test
    void emptyConfigurationDoesNotDiscoverWorlds() throws IOException {
        createWorld("world");
        assertEquals(List.of(), WorldTargets.validate(container, List.of()));
    }

    @Test
    void rejectsTraversalContainerAndAbsolutePaths() {
        for (String name : List.of("../outside", ".", " ", container.toString())) {
            assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of(name)));
        }
    }

    @Test
    void rejectsMissingMetadata() throws IOException {
        Files.createDirectories(container.resolve("missing/region"));
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("missing")));
    }

    @Test
    void rejectsDuplicateAndNestedTargets() throws IOException {
        createWorld("world");
        createWorld("world/nested");
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world", "world")));
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world", "world/nested")));
    }

    private Path createWorld(String name) throws IOException {
        Path world = container.resolve(name);
        Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "fixture");
        return world;
    }

    @Test
    void acceptsNestedOverworldLayout() throws IOException {
        Path world = container.resolve("modern");
        Files.createDirectories(world.resolve("dimensions/minecraft/overworld/region"));
        Files.writeString(world.resolve("level.dat"), "fixture");
        assertEquals(List.of(world.toRealPath()), WorldTargets.validate(container, List.of("modern")));
    }

    @Test
    void rejectsNetherDirectoryWithoutWorldMetadata() throws IOException {
        Files.createDirectories(container.resolve("world/DIM-1/region"));
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world/DIM-1")));
    }

    @Test
    void rejectsFileInPlaceOfRegionDirectory() throws IOException {
        Path world = Files.createDirectory(container.resolve("broken"));
        Files.writeString(world.resolve("level.dat"), "fixture");
        Files.writeString(world.resolve("region"), "not a directory");
        assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("broken")));
    }

    @Test
    void acceptsDistinctUnicodeAndSpacedNames() throws IOException {
        Path first = createWorld("World with spaces");
        Path second = createWorld("Água 世界");
        assertEquals(List.of(first.toRealPath(), second.toRealPath()),
                WorldTargets.validate(container, List.of("World with spaces", "Água 世界")));
    }
}
