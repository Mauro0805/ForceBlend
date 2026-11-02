package com.mauro0805.forceblend.bukkit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class WorldTargets {
    private WorldTargets() {}

    static List<Path> validate(Path container, List<String> names) throws IOException {
        Path root = container.toRealPath();
        List<Path> targets = new ArrayList<>();
        for (String name : names) {
            if (name.isBlank()) throw new IOException("Blank world target");
            Path relative = Path.of(name);
            if (relative.isAbsolute() || relative.getRoot() != null) {
                throw new IOException("World target must be relative: " + name);
            }
            Path candidate = root.resolve(relative).normalize();
            if (!candidate.startsWith(root) || candidate.equals(root)) {
                throw new IOException("Target escapes or equals the container: " + name);
            }
            Path cursor = root;
            for (Path segment : root.relativize(candidate)) {
                cursor = cursor.resolve(segment);
                rejectAlias(cursor);
            }
            Path target = candidate.toRealPath();
            Path terrain = terrainDirectory(target);
            if (!target.startsWith(root)
                    || !Files.isDirectory(terrain.resolve("region"), LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(target.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Expected an existing level.dat and region directory: " + name);
            }
            for (Path existing : targets) {
                if (target.startsWith(existing) || existing.startsWith(target)) {
                    throw new IOException("Duplicate or overlapping targets: " + name);
                }
            }
            targets.add(target);
        }
        return List.copyOf(targets);
    }

    static Path terrainDirectory(Path world) throws IOException {
        Path modern = world.resolve("dimensions/minecraft/overworld");
        Path cursor = world;
        for (Path segment : world.relativize(modern.resolve("region"))) {
            cursor = cursor.resolve(segment);
            rejectAlias(cursor);
        }
        if (Files.isDirectory(modern.resolve("region"), LinkOption.NOFOLLOW_LINKS)) return modern;
        rejectAlias(world.resolve("region"));
        return world;
    }

    private static void rejectAlias(Path path) throws IOException {
        if (Files.isSymbolicLink(path) || Files.exists(path, LinkOption.NOFOLLOW_LINKS)
                && !path.toRealPath().equals(path.toAbsolutePath().normalize())) {
            throw new IOException("Linked or redirected world path: " + path);
        }
    }
}
