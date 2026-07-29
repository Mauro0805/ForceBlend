package com.mauro0805.forceblend.client;

import com.mauro0805.forceblend.backup.BackupProgress;
import com.mauro0805.forceblend.backup.BackupResult;
import com.mauro0805.forceblend.backup.WorldBackupService;
import com.mauro0805.forceblend.client.config.ForceBlendConfigController;
import com.mauro0805.forceblend.client.ui.*;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.processing.*;
import com.mauro0805.forceblend.version.BlendingProfile;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Loader-facing world lifecycle bridge shared by every version-specific mixin.
 */
public final class WorldOpenHandler {
    private static final int PROGRESS_WINDOW_CHUNK_THRESHOLD = 1_000;
    private static final int ANALYSIS_WINDOW_REGION_THRESHOLD = 8;
    private static final Duration PROGRESS_WINDOW_DURATION_THRESHOLD = Duration.ofSeconds(2L);
    private static final long FALLBACK_CHUNKS_PER_SECOND = 3_000L;
    private static final long FALLBACK_BYTES_PER_SECOND = 150L * 1_024L * 1_024L;
    private static final Path MODERN_OVERWORLD_PATH = Path.of(
        "dimensions",
        "minecraft",
        "overworld"
    );

    private WorldOpenHandler() {
    }

    /**
     * @return {@code true} to continue the intercepted world-open call, or {@code false} to cancel it
     */
    public static boolean beforeWorldOpen(String levelId) {
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
        ForceBlendConfig config = ForceBlendConfigController.load();
        if (!config.automaticProcessingEnabled()) {
            PreAnalysisDecision preAnalysisDecision = ForceBlendDesktopUi.choosePreAnalysisAction(levelId);
            if (preAnalysisDecision == PreAnalysisDecision.CANCEL) {
                ForceBlendClient.LOGGER.info("World '{}' opening was cancelled before ForceBlend analysis", levelId);
                return false;
            }
            if (preAnalysisDecision == PreAnalysisDecision.ENTER_UNCHANGED) {
                ForceBlendClient.LOGGER.info(
                    "Entering world '{}' without ForceBlend analysis or modification",
                    levelId
                );
                return true;
            }
        }

        int maximumChunks = maximumChunks(config.maximumChunksPerRun());
        int processingThreads = config.resolvedProcessingThreads(Runtime.getRuntime().availableProcessors());
        ProcessingOptions options = new ProcessingOptions(processingThreads, maximumChunks);
        AtomicReference<ForceBlendProgressWindow> progressWindow = new AtomicReference<>(
            ForceBlendProgressWindow.hidden()
        );
        AtomicBoolean analysisWindowRequested = new AtomicBoolean();
        ProcessingProgressListener progressListener = new ProcessingProgressListener() {
            @Override
            public void onProgress(ProcessingProgress progress) {
                if (config.detailedLoggingEnabled()) {
                    logDetailedProgress(levelId, progress);
                }
                if (progress.phase() == ProcessingPhase.ANALYZING
                    && progress.totalRegions() >= ANALYSIS_WINDOW_REGION_THRESHOLD
                    && analysisWindowRequested.compareAndSet(false, true)) {
                    progressWindow.set(ForceBlendDesktopUi.openProgress(levelId, true));
                }
                progressWindow.get().update(toProgressView(progress));
            }

            @Override
            public boolean detailedEventsEnabled() {
                return config.detailedLoggingEnabled();
            }

            @Override
            public void onRegionEvent(ProcessingRegionEvent event) {
                ForceBlendClient.LOGGER.info(
                    "ForceBlend region event for '{}': phase={}, state={}, region={}, chunks={}, duration={} ms",
                    levelId,
                    event.phase(),
                    event.state(),
                    event.regionFile(),
                    event.chunks(),
                    event.duration().toMillis()
                );
            }

            @Override
            public void onChunkEvent(ChunkProcessingEvent event) {
                ForceBlendClient.LOGGER.info(
                    "ForceBlend chunk event for '{}': phase={}, region={}, chunk=[{}, {}], outcome={}, "
                        + "selected={}, elapsed={} ms",
                    levelId,
                    event.phase(),
                    event.regionFile(),
                    event.chunkX(),
                    event.chunkZ(),
                    event.outcome(),
                    event.selected(),
                    event.elapsed().toMillis()
                );
            }
        };
        long startNanos = System.nanoTime();

        ForceBlendClient.LOGGER.info(
            "Analyzing the current ForceBlend terrain frontier for world '{}' at {} using the {} marker",
            levelId,
            overworldDirectory,
            profile.format()
        );
        try {
            ProcessingPlan plan = WorldProcessor.analyze(
                levelId,
                overworldDirectory,
                profile,
                options,
                progressListener
            );
            ProcessingPlanSummary summary = plan.summary();
            ForceBlendProgressWindow analysisWindow = progressWindow.getAndSet(ForceBlendProgressWindow.hidden());
            analysisWindow.close();
            if (summary.selectedChunks() == 0) {
                ForceBlendClient.LOGGER.info(
                    "ForceBlend found no chunks selected for preparation in world '{}' ({} scanned, {} affected)",
                    levelId,
                    summary.discoveredChunks(),
                    summary.affectedChunks()
                );
                ForceBlendDesktopUi.showNoChanges(levelId);
                return true;
            }

            Duration estimatedDuration = plan.estimatedApplyDuration().orElseGet(() -> fallbackEstimate(summary));
            WorldOpenDecision decision = ForceBlendDesktopUi.chooseWorldAction(
                levelId,
                new WorldAnalysisView(
                    summary.affectedChunks(),
                    summary.selectedChunks(),
                    summary.deferredChunks(),
                    summary.affectedRegions(),
                    summary.discoveredChunks(),
                    estimatedDuration
                ),
                config.automaticBackupEnabled()
            );
            if (decision == WorldOpenDecision.CANCEL) {
                ForceBlendClient.LOGGER.info("World '{}' opening was cancelled from the ForceBlend prompt", levelId);
                return false;
            }
            if (decision == WorldOpenDecision.ENTER_UNCHANGED) {
                ForceBlendClient.LOGGER.info("Entering world '{}' without applying ForceBlend changes", levelId);
                return true;
            }

            boolean createBackup = config.automaticBackupEnabled()
                || decision == WorldOpenDecision.APPLY_WITH_BACKUP;
            boolean showProgress = createBackup
                || summary.selectedChunks() >= PROGRESS_WINDOW_CHUNK_THRESHOLD
                || estimatedDuration.compareTo(PROGRESS_WINDOW_DURATION_THRESHOLD) >= 0;
            ForceBlendProgressWindow openedProgress = ForceBlendDesktopUi.openProgress(levelId, showProgress);
            progressWindow.set(openedProgress);
            boolean completed = false;
            try {
                if (createBackup) {
                    ForceBlendClient.LOGGER.info(
                        "Creating a complete ForceBlend backup for world '{}' before processing",
                        levelId
                    );
                    BackupResult backup = createBackup(worldDirectory, levelId, openedProgress);
                    ForceBlendClient.LOGGER.info(
                        "ForceBlend backup completed for world '{}': {} files, {} bytes at {}",
                        levelId,
                        backup.files(),
                        backup.uncompressedBytes(),
                        backup.archive()
                    );
                }
                long applicationStartNanos = System.nanoTime();
                ProcessingReport report = WorldProcessor.apply(plan, progressListener);
                long completionNanos = System.nanoTime();
                long applicationMillis = TimeUnit.NANOSECONDS.toMillis(
                    completionNanos - applicationStartNanos
                );
                long workflowMillis = TimeUnit.NANOSECONDS.toMillis(completionNanos - startNanos);
                logCompletion(
                    levelId,
                    overworldDirectory,
                    report,
                    summary,
                    applicationMillis,
                    workflowMillis,
                    config.detailedLoggingEnabled()
                );
                openedProgress.complete(
                    formatCount(report.preparedChunks() + report.repairedChunks()) + " chunks prepared"
                );
                completed = true;
                return true;
            } finally {
                progressWindow.set(ForceBlendProgressWindow.hidden());
                if (!completed) {
                    openedProgress.close();
                }
            }
        } catch (IOException | RuntimeException exception) {
            progressWindow.get().close();
            ForceBlendDesktopUi.showFailure(levelId, exception);
            return false;
        }
    }

    private static BackupResult createBackup(
        Path worldDirectory,
        String levelId,
        ForceBlendProgressWindow progressWindow
    ) throws IOException {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        WorldBackupService backupService = new WorldBackupService();
        return backupService.createBackup(
            worldDirectory,
            WorldBackupService.defaultBackupRoot(gameDirectory),
            levelId,
            () -> false,
            progress -> updateBackupProgress(progressWindow, progress)
        );
    }

    private static void updateBackupProgress(ForceBlendProgressWindow window, BackupProgress progress) {
        window.updateBackup(
            progress.totalFiles(),
            progress.totalBytes(),
            progress.completedFiles(),
            progress.completedBytes(),
            progress.currentEntry()
        );
    }

    private static ProcessingProgressView toProgressView(ProcessingProgress progress) {
        String currentRegions = formatActiveRegions(progress);
        return new ProcessingProgressView(
            phaseLabel(progress),
            progress.completedRegions(),
            progress.totalRegions(),
            progress.completedChunks(),
            Math.max(0, progress.totalChunks()),
            progress.affectedChunks(),
            currentRegions,
            progress.elapsed(),
            progress.estimatedRemaining().orElse(Duration.ZERO)
        );
    }

    private static String formatActiveRegions(ProcessingProgress progress) {
        if (progress.activeRegions().isEmpty()) {
            return "Waiting...";
        }
        int visibleRegions = Math.min(3, progress.activeRegions().size());
        String visible = String.join(", ", progress.activeRegions().subList(0, visibleRegions));
        int hiddenRegions = progress.activeRegions().size() - visibleRegions;
        return hiddenRegions == 0 ? visible : visible + " (+" + hiddenRegions + " more)";
    }

    private static String phaseLabel(ProcessingProgress progress) {
        return switch (progress.phase()) {
            case ANALYZING -> "Analyzing terrain borders...";
            case ANALYSIS_COMPLETE -> "Analysis complete";
            case VALIDATING -> "Checking that region files are unchanged...";
            case APPLYING -> "Preparing terrain...";
            case COMPLETE -> "Terrain preparation complete";
        };
    }

    private static Duration fallbackEstimate(ProcessingPlanSummary summary) {
        long chunkMillis = Math.max(1L, summary.selectedChunks()) * 1_000L / FALLBACK_CHUNKS_PER_SECOND;
        double estimatedByteMillis = (double) summary.selectedRegionBytes() * 1_000.0 / FALLBACK_BYTES_PER_SECOND;
        long byteMillis = estimatedByteMillis >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) estimatedByteMillis;
        return Duration.ofMillis(Math.max(1_000L, Math.max(chunkMillis, byteMillis)));
    }

    private static int maximumChunks(long configuredMaximum) {
        if (configuredMaximum == ForceBlendConfig.UNLIMITED_CHUNKS) {
            return ProcessingOptions.UNLIMITED_CHUNKS;
        }
        return configuredMaximum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) configuredMaximum;
    }

    private static void logDetailedProgress(String levelId, ProcessingProgress progress) {
        ForceBlendClient.LOGGER.info(
            "ForceBlend progress for '{}': phase={}, regions={}/{}, chunks={}/{}, active={}, elapsed={} ms",
            levelId,
            progress.phase(),
            progress.completedRegions(),
            progress.totalRegions(),
            progress.completedChunks(),
            progress.totalChunks(),
            progress.activeRegions(),
            progress.elapsed().toMillis()
        );
    }

    private static void logCompletion(
        String levelId,
        Path overworldDirectory,
        ProcessingReport report,
        ProcessingPlanSummary summary,
        long applicationMillis,
        long workflowMillis,
        boolean detailedLoggingEnabled
    ) {
        if (report.regionFiles() == 0) {
            ForceBlendClient.LOGGER.info(
                "No existing Overworld region files require processing for world '{}' at {}",
                levelId,
                overworldDirectory.resolve("region")
            );
        }
        ForceBlendClient.LOGGER.info(
            "ForceBlend completed world '{}': {} regions scanned, {} regions rewritten, {} chunks discovered, "
                + "{} frontier chunks, {} prepared, {} unsafe markers repaired, {} skipped, {} already prepared "
                + "({} ms processing, {} ms total workflow)",
            levelId,
            report.regionFiles(),
            summary.selectedRegions(),
            report.discoveredChunks(),
            report.frontierChunks(),
            report.preparedChunks(),
            report.repairedChunks(),
            report.skippedChunks(),
            report.alreadyPreparedChunks(),
            applicationMillis,
            workflowMillis
        );
        if (detailedLoggingEnabled) {
            double elapsedSeconds = Math.max(1L, applicationMillis) / 1_000.0;
            double averageChunksPerSecond = summary.selectedChunks() / elapsedSeconds;
            ForceBlendClient.LOGGER.info(
                "ForceBlend detailed totals for '{}': average speed={} chunks/s, selected={}, deferred={}, "
                    + "affectedRegions={}, rewrittenRegions={}",
                levelId,
                String.format(Locale.ROOT, "%.1f", averageChunksPerSecond),
                summary.selectedChunks(),
                summary.deferredChunks(),
                summary.affectedRegions(),
                summary.selectedRegions()
            );
        }
    }

    private static String formatCount(int count) {
        return java.text.NumberFormat.getIntegerInstance(Locale.getDefault()).format(count);
    }
}
