package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Manual integration test that processes a temporary copy of a real Overworld.
 * The source directory is never modified.
 */
public final class ForceBlendWorldCopyTest {
    private static final Path MODERN_OVERWORLD_PATH = Path.of(
        "dimensions",
        "minecraft",
        "overworld"
    );

    private ForceBlendWorldCopyTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                "Provide the world or Overworld dimension directory and Minecraft version"
            );
        }

        Path suppliedPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path sourceWorld = resolveWorldDirectory(suppliedPath);
        Path modernOverworld = sourceWorld.resolve(MODERN_OVERWORLD_PATH);
        Path sourceOverworld = Files.isDirectory(modernOverworld.resolve("region"))
            ? modernOverworld
            : sourceWorld;
        Path sourceRegion = sourceOverworld.resolve("region");
        if (!Files.isDirectory(sourceRegion)) {
            throw new IOException("Overworld region directory not found under " + suppliedPath);
        }

        Path temporaryRoot = Files.createTempDirectory("forceblend-world-copy-test-");
        try {
            byte[] sourceHashBefore = hashDirectory(sourceRegion);
            Path copiedWorld = temporaryRoot.resolve("world");
            Path copiedOverworld = copiedWorld.resolve("overworld");
            Files.createDirectories(copiedWorld);
            Files.copy(sourceWorld.resolve("level.dat"), copiedWorld.resolve("level.dat"));
            Path modernWorldGenSettings = sourceWorld.resolve(
                Path.of("data", "minecraft", "world_gen_settings.dat")
            );
            if (Files.isRegularFile(modernWorldGenSettings)) {
                Path copiedSettings = copiedWorld.resolve(
                    Path.of("data", "minecraft", "world_gen_settings.dat")
                );
                Files.createDirectories(copiedSettings.getParent());
                Files.copy(modernWorldGenSettings, copiedSettings);
            }
            copyDirectory(sourceRegion, copiedOverworld.resolve("region"));

            BlendingProfile profile = SupportedMinecraftVersions.profileFor(args[1]);
            ProcessingReport report = WorldProcessor.process("world-copy-test", copiedOverworld, profile);
            if (report.regionFiles() == 0 || report.discoveredChunks() == 0) {
                throw new AssertionError("The copied world contained no processable regions or chunks");
            }
            if (report.discoveredChunks() != report.preparedChunks()
                + report.repairedChunks()
                + report.skippedChunks()
                + report.alreadyPreparedChunks()) {
                throw new AssertionError("The processing report contains inconsistent chunk counts");
            }

            byte[] copiedHashAfterMigration = hashDirectory(copiedOverworld.resolve("region"));
            ProcessingReport secondReport = WorldProcessor.process("world-copy-test", copiedOverworld, profile);
            if (secondReport.preparedChunks() != 0
                || secondReport.repairedChunks() != 0
                || !Arrays.equals(copiedHashAfterMigration, hashDirectory(copiedOverworld.resolve("region")))) {
                throw new AssertionError("The copied world was not binary-idempotent on the second frontier scan");
            }
            if (!Arrays.equals(sourceHashBefore, hashDirectory(sourceRegion))) {
                throw new AssertionError("The source region directory changed during the copy test");
            }

            System.out.printf(
                "Real-world frontier copy verified: %d regions, %d chunks, %d frontier, %d prepared, %d repaired, "
                    + "%d skipped, %d already prepared%n",
                report.regionFiles(),
                report.discoveredChunks(),
                report.frontierChunks(),
                report.preparedChunks(),
                report.repairedChunks(),
                report.skippedChunks(),
                report.alreadyPreparedChunks()
            );
        } finally {
            deleteRecursively(temporaryRoot);
        }
    }

    private static Path resolveWorldDirectory(Path suppliedPath) throws IOException {
        if (Files.isRegularFile(suppliedPath.resolve("level.dat"))) {
            return suppliedPath;
        }
        Path candidate = suppliedPath;
        for (int depth = 0; depth < 3 && candidate.getParent() != null; depth++) {
            candidate = candidate.getParent();
        }
        if (Files.isRegularFile(candidate.resolve("level.dat"))) {
            return candidate;
        }
        throw new IOException("World level.dat not found from supplied path " + suppliedPath);
    }

    private static byte[] hashDirectory(Path directory) throws IOException {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                digest.update(directory.relativize(path).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(path));
            }
        }
        return digest.digest();
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path destination = target.resolve(source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
