package com.mauro0805.forceblend.client;

import com.mauro0805.forceblend.processing.ProcessingReport;
import com.mauro0805.forceblend.processing.WorldProcessor;
import com.mauro0805.forceblend.version.BlendingProfile;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Loader-facing world lifecycle bridge shared by every version-specific mixin.
 */
public final class WorldOpenHandler {
    private static final Path MODERN_OVERWORLD_PATH = Path.of(
        "dimensions",
        "minecraft",
        "overworld"
    );

    private WorldOpenHandler() {
    }

    public static void beforeWorldOpen(String levelId) {
        Path savesDirectory = FabricLoader.getInstance().getGameDir().resolve("saves").toAbsolutePath().normalize();
        Path worldDirectory = savesDirectory.resolve(levelId).normalize();
        if (!worldDirectory.startsWith(savesDirectory)) {
            throw new IllegalArgumentException("World id resolves outside the Minecraft saves directory: " + levelId);
        }

        Path modernOverworld = worldDirectory.resolve(MODERN_OVERWORLD_PATH);
        Path overworldDirectory = Files.isDirectory(modernOverworld.resolve("region"))
            ? modernOverworld
            : worldDirectory;
        String minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Minecraft version metadata is unavailable"));
        BlendingProfile profile = SupportedMinecraftVersions.profileFor(minecraftVersion);
        long startNanos = System.nanoTime();

        ForceBlendClient.LOGGER.info(
            "Scanning the current ForceBlend terrain frontier for world '{}' at {} using the {} marker",
            levelId,
            overworldDirectory,
            profile.format()
        );
        try {
            ProcessingReport report = WorldProcessor.process(levelId, overworldDirectory, profile);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            if (report.regionFiles() == 0) {
                ForceBlendClient.LOGGER.info(
                    "No existing Overworld region files require processing for world '{}' at {}",
                    levelId,
                    overworldDirectory.resolve("region")
                );
            }
            ForceBlendClient.LOGGER.info(
                "ForceBlend completed the frontier scan for world '{}': {} regions, {} chunks discovered, "
                    + "{} complete frontier chunks selected, {} prepared, {} unsafe markers repaired, "
                    + "{} partial, interior, or native-migration chunks skipped, {} already prepared ({} ms)",
                levelId,
                report.regionFiles(),
                report.discoveredChunks(),
                report.frontierChunks(),
                report.preparedChunks(),
                report.repairedChunks(),
                report.skippedChunks(),
                report.alreadyPreparedChunks(),
                elapsedMillis
            );
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(
                "ForceBlend could not safely prepare the Overworld for world '" + levelId + "'",
                exception
            );
        }
    }
}
