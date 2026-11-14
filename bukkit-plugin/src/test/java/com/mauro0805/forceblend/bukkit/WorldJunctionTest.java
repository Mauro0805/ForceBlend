package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WorldJunctionTest {
    @TempDir Path root;

    @Test
    void rejectsJunctionTargetsRootTerrainAndPreferredNestedTerrain() throws Exception {
        Path outside = Files.createDirectories(root.resolve("outside/region")).getParent();
        Files.writeString(outside.resolve("level.dat"), "directory fixture");
        Path container = Files.createDirectory(root.resolve("container"));
        Path world = Files.createDirectories(container.resolve("world/region")).getParent();
        Files.writeString(world.resolve("level.dat"), "directory fixture");
        Path nested = Files.createDirectories(world.resolve("dimensions/minecraft/overworld"));
        for (Path alias : List.of(container.resolve("linked-world"), nested.resolve("region"), world.resolve("redirected-region"))) {
            Path destination = alias.equals(container.resolve("linked-world")) ? outside : outside.resolve("region");
            Process process = new ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.toString(), destination.toString())
                    .redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Junction fixture creation timed out");
                assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes()));
                assertFalse(alias.toRealPath().equals(alias.toAbsolutePath()), "Fixture must redirect identity");
                if (alias.getFileName().toString().equals("linked-world")) {
                    assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("linked-world")));
                } else if (alias.equals(nested.resolve("region"))) {
                    assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world")));
                } else {
                    Files.delete(world.resolve("region"));
                    Files.move(alias, world.resolve("region"));
                    try { assertThrows(IOException.class, () -> WorldTargets.validate(container, List.of("world"))); }
                    finally { Files.deleteIfExists(world.resolve("region")); }
                }
            } finally {
                if (process.isAlive()) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS); }
                process.getInputStream().close();
                process.getOutputStream().close();
                process.getErrorStream().close();
                Files.deleteIfExists(alias);
            }
        }
    }
}
