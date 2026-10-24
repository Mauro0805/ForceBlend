package com.mauro0805.forceblend;

import com.mauro0805.forceblend.backup.BackupCancellationToken;
import com.mauro0805.forceblend.backup.BackupProgressListener;
import com.mauro0805.forceblend.backup.BackupResult;
import com.mauro0805.forceblend.backup.WorldBackupService;
import com.mauro0805.forceblend.config.ConfigLoadResult;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.ForceBlendConfigStore;
import com.mauro0805.forceblend.config.RunMode;
import com.mauro0805.forceblend.processing.ChunkProcessingEvent;
import com.mauro0805.forceblend.processing.ProcessingOptions;
import com.mauro0805.forceblend.processing.ProcessingPlan;
import com.mauro0805.forceblend.processing.ProcessingProgress;
import com.mauro0805.forceblend.processing.ProcessingProgressListener;
import com.mauro0805.forceblend.processing.ProcessingRegionEvent;
import com.mauro0805.forceblend.processing.ProcessingReport;
import com.mauro0805.forceblend.processing.WorldProcessor;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class WorldPreparer {
    public static final Logger LOGGER = LoggerFactory.getLogger("forceblend");
    private static final Path MODERN_OVERWORLD_PATH = Path.of("dimensions", "minecraft", "overworld");

    private WorldPreparer() {
    }

    public static Path overworldDirectory(Path worldDirectory) {
        Path modern = worldDirectory.resolve(MODERN_OVERWORLD_PATH);
        return Files.isDirectory(modern.resolve("region")) ? modern : worldDirectory;
    }

    public static boolean shouldSkip(Path worldDirectory, ForceBlendConfig config) throws IOException {
        return config.runMode() == RunMode.ONCE && WorldPreparationState.isComplete(worldDirectory);
    }

    public static ProcessingPlan analyze(
        String levelId,
        Path worldDirectory,
        String minecraftVersion,
        ForceBlendConfig config,
        ProcessingProgressListener listener
    ) throws IOException {
        int maximumChunks = config.hasChunkLimit()
            ? (int) Math.min(Integer.MAX_VALUE, config.maximumChunksPerRun())
            : ProcessingOptions.UNLIMITED_CHUNKS;
        ProcessingOptions options = new ProcessingOptions(
            config.resolvedProcessingThreads(Runtime.getRuntime().availableProcessors()),
            maximumChunks
        );
        return WorldProcessor.analyze(
            levelId,
            overworldDirectory(worldDirectory),
            SupportedMinecraftVersions.profileFor(minecraftVersion),
            options,
            listener
        );
    }

    public static ProcessingReport apply(
        ProcessingPlan plan,
        Path worldDirectory,
        Path gameDirectory,
        ForceBlendConfig config,
        boolean backupRequired,
        BackupProgressListener backupListener,
        ProcessingProgressListener progressListener
    ) throws IOException {
        if (plan.summary().discoveredChunks() > 0 && (backupRequired || config.automaticBackupEnabled())) {
            LOGGER.info("Creating a complete backup for world '{}' before processing", plan.levelId());
            BackupResult backup = new WorldBackupService().createBackup(
                worldDirectory,
                WorldBackupService.defaultBackupRoot(gameDirectory),
                plan.levelId(),
                BackupCancellationToken.NEVER_CANCELLED,
                backupListener
            );
            LOGGER.info(
                "Backup completed for '{}': {} files, {} bytes at {}",
                plan.levelId(),
                backup.files(),
                backup.uncompressedBytes(),
                backup.archive()
            );
        }
        ProcessingReport report = WorldProcessor.apply(plan, progressListener);
        if (config.runMode() == RunMode.ONCE
            && plan.summary().discoveredChunks() > 0
            && plan.summary().deferredChunks() == 0) {
            WorldPreparationState.markComplete(worldDirectory);
        }
        LOGGER.info(
            "ForceBlend completed '{}': {} chunks prepared, {} unsafe markers repaired, {} deferred",
            plan.levelId(),
            report.preparedChunks(),
            report.repairedChunks(),
            plan.summary().deferredChunks()
        );
        return report;
    }

    public static Optional<ProcessingReport> prepare(
        String levelId,
        Path worldDirectory,
        Path gameDirectory,
        String minecraftVersion
    ) throws IOException {
        ConfigLoadResult loaded = new ForceBlendConfigStore(ForceBlendConfigStore.defaultPath(gameDirectory))
            .loadOrCreate();
        loaded.warnings().forEach(warning -> LOGGER.warn("ForceBlend config: {}", warning));
        ForceBlendConfig config = loaded.config();
        if (!config.automaticProcessingEnabled()) {
            LOGGER.info("Automatic processing is disabled; skipping world '{}'", levelId);
            return Optional.empty();
        }
        if (shouldSkip(worldDirectory, config)) {
            LOGGER.info("World '{}' is already prepared; once-per-world mode skips it", levelId);
            return Optional.empty();
        }
        ProcessingProgressListener listener = headlessProgress(config.detailedLoggingEnabled());
        ProcessingPlan plan = analyze(levelId, worldDirectory, minecraftVersion, config, listener);
        if (plan.summary().discoveredChunks() == 0) {
            LOGGER.info("No existing Overworld chunks found for '{}'; completion was not recorded", levelId);
            return Optional.empty();
        }
        return Optional.of(apply(
            plan,
            worldDirectory,
            gameDirectory,
            config,
            true,
            BackupProgressListener.NONE,
            listener
        ));
    }

    private static ProcessingProgressListener headlessProgress(boolean detailed) {
        return new ProcessingProgressListener() {
            private long nextProgressNanos;

            @Override
            public void onProgress(ProcessingProgress progress) {
                long now = System.nanoTime();
                if (now >= nextProgressNanos) {
                    LOGGER.info(
                        "ForceBlend {}: {}/{} regions, {} chunks completed, active={}",
                        progress.phase(),
                        progress.completedRegions(),
                        progress.totalRegions(),
                        progress.completedChunks(),
                        progress.activeRegions()
                    );
                    nextProgressNanos = now + 5_000_000_000L;
                }
            }

            @Override
            public boolean detailedEventsEnabled() {
                return detailed;
            }

            @Override
            public void onRegionEvent(ProcessingRegionEvent event) {
                LOGGER.info("ForceBlend region: {}", event);
            }

            @Override
            public void onChunkEvent(ChunkProcessingEvent event) {
                LOGGER.info("ForceBlend chunk: {}", event);
            }
        };
    }
}
