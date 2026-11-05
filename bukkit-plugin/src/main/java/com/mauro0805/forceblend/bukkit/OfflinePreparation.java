package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.WorldPreparationState;
import com.mauro0805.forceblend.backup.BackupCancellationToken;
import com.mauro0805.forceblend.backup.WorldBackupService;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.RunMode;
import com.mauro0805.forceblend.processing.ChunkProcessingEvent;
import com.mauro0805.forceblend.processing.ProcessingOptions;
import com.mauro0805.forceblend.processing.ProcessingProgress;
import com.mauro0805.forceblend.processing.ProcessingProgressListener;
import com.mauro0805.forceblend.processing.ProcessingRegionEvent;
import com.mauro0805.forceblend.processing.WorldProcessor;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.jpountz.lz4.LZ4Factory;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ByteTag;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

final class OfflinePreparation {
    private OfflinePreparation() {}

    static void run(List<Path> targets, Path backupBase, String version, ForceBlendConfig config, Logger logger)
            throws IOException {
        run(targets, backupBase, version, config, logger, false);
    }

    static void run(List<Path> targets, Path backupBase, String version, ForceBlendConfig config, Logger logger,
                    boolean analysisOnly) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Preparation cancelled before worker startup");
        if (!config.automaticProcessingEnabled()) {
            logger.info("Automatic preparation is disabled");
            return;
        }
        List<String> command = new ArrayList<>();
        WorkerBudget budget = WorkerBudget.configured(config.processingThreads(), Runtime.getRuntime().availableProcessors());
        logger.info("Worker resources: heap=" + budget.heapMiB() + " MiB; processing threads=" + budget.threads());
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        command.addAll(List.of("-Xms32m", "-Xmx" + budget.heapMiB() + "m", "-Dfile.encoding=UTF-8", "-cp"));
        List<String> roots = new ArrayList<>();
        try {
            for (Class<?> type : List.of(OfflinePreparationWorker.class, NBTDeserializer.class, LZ4Factory.class)) {
                String location = Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
                if (!roots.contains(location)) roots.add(location);
            }
        } catch (Exception exception) {
            throw new IOException("Cannot locate worker dependencies", exception);
        }
        command.add(String.join(File.pathSeparator, roots));
        command.addAll(List.of(OfflinePreparationWorker.class.getName(), backupBase.toAbsolutePath().toString(), version,
                Long.toString(config.maximumChunksPerRun()), Integer.toString(budget.threads()),
                config.runMode().name(), Boolean.toString(config.detailedLoggingEnabled()), Boolean.toString(analysisOnly)));
        targets.forEach(target -> command.add(target.toString()));
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        for (String key : List.of("_JAVA_OPTIONS", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS")) builder.environment().remove(key);
        supervise(builder.start(), logger::info);
    }

    static void supervise(Process worker, Consumer<String> log) throws IOException {
        AtomicReference<IOException> outputFailure = new AtomicReference<>();
        Thread reader = new Thread(() -> {
            try (BufferedReader output = worker.inputReader(StandardCharsets.UTF_8)) {
                String line;
                while ((line = output.readLine()) != null) log.accept(line);
            } catch (IOException | RuntimeException exception) {
                outputFailure.set(new IOException("Cannot read preparation worker output", exception));
                worker.destroyForcibly();
            }
        }, "forceblend-worker-output");
        reader.setDaemon(true);
        Throwable failure = null;
        try {
            reader.start();
            worker.waitFor();
            reader.join(5_000);
            if (reader.isAlive()) throw new IOException("Preparation worker output did not close");
            if (outputFailure.get() != null) throw outputFailure.get();
            if (worker.exitValue() != 0) {
                throw new IOException("Offline preparation worker failed; retain backups for recovery");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Offline preparation was interrupted", exception);
            failure = interrupted;
            throw interrupted;
        } catch (IOException | RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            boolean interrupted = Thread.interrupted();
            IOException cleanupFailure = null;
            try {
                if (worker.isAlive()) worker.destroyForcibly();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (worker.isAlive() && System.nanoTime() < deadline) {
                    try {
                        worker.waitFor(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        interrupted = true;
                    }
                }
                if (worker.isAlive()) cleanupFailure = new IOException("Preparation worker did not exit");
                for (var stream : List.of(worker.getOutputStream(), worker.getInputStream(), worker.getErrorStream())) {
                    try {
                        stream.close();
                    } catch (IOException exception) {
                        if (cleanupFailure == null) {
                            cleanupFailure = exception;
                        } else {
                            cleanupFailure.addSuppressed(exception);
                        }
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt();
            }
            if (cleanupFailure != null) {
                if (failure != null) failure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    static void execute(List<Path> targets, Path backupBase, String version, ForceBlendConfig config,
                        Consumer<String> log, WorldLeases leases, boolean analysisOnly) throws IOException {
        var profile = SupportedMinecraftVersions.profileFor(version);
        for (Path target : targets) validateVersion(target, version);
        ProcessingProgressListener progress = new ProcessingProgressListener() {
            private long nextLog;
            @Override public void onProgress(ProcessingProgress event) {
                long now = System.nanoTime();
                if (now >= nextLog) {
                    log.accept("Preparation progress: " + event.phase() + "; regions="
                            + event.completedRegions() + "/" + event.totalRegions()
                            + "; chunks=" + event.completedChunks() + "/" + event.totalChunks()
                            + "; active=" + event.activeRegions() + "; remaining milliseconds="
                            + event.estimatedRemaining().map(duration -> Long.toString(duration.toMillis())).orElse("unknown"));
                    nextLog = now + 5_000_000_000L;
                }
            }
            @Override public boolean detailedEventsEnabled() { return config.detailedLoggingEnabled(); }
            @Override public void onRegionEvent(ProcessingRegionEvent event) { log.accept("Region: " + event); }
            @Override public void onChunkEvent(ChunkProcessingEvent event) { log.accept("Chunk: " + event); }
        };
        for (Path target : targets) {
            if (config.runMode() == RunMode.ONCE && WorldPreparationState.isComplete(target)) {
                log.accept("Once mode skipped completed world: " + target.getFileName());
                continue;
            }
            Path terrain = WorldTargets.terrainDirectory(target);
            int maximum = config.hasChunkLimit() ? (int) Math.min(Integer.MAX_VALUE, config.maximumChunksPerRun())
                    : ProcessingOptions.UNLIMITED_CHUNKS;
            var plan = WorldProcessor.analyze(target.getFileName().toString(), terrain,
                    profile,
                    new ProcessingOptions(WorkerBudget.forHeap(config.processingThreads(),
                            Runtime.getRuntime().availableProcessors(),
                            (int) (Runtime.getRuntime().maxMemory() / (1024L * 1024L))).threads(), maximum), progress);
            if (plan.summary().discoveredChunks() == 0) {
                log.accept("No terrain found; completion was not recorded: " + target.getFileName());
                continue;
            }
            log.accept("Analysis completed: affected=" + plan.summary().affectedChunks()
                    + "; selected=" + plan.summary().selectedChunks() + "; deferred=" + plan.summary().deferredChunks()
                    + "; regions=" + plan.summary().selectedRegions() + "/" + plan.summary().regionFiles()
                    + "; estimated apply milliseconds=" + plan.estimatedApplyDuration()
                    .map(duration -> Long.toString(duration.toMillis())).orElse("unknown") + " (backup excluded)");
            if (analysisOnly) continue;
            leases.validate();
            long[] nextBackupLog = {0};
            var backup = new WorldBackupService().createBackup(target,
                    WorldBackupService.defaultBackupRoot(backupBase), target.getFileName().toString(),
                    BackupCancellationToken.NEVER_CANCELLED, event -> {
                        long now = System.nanoTime();
                        if (now >= nextBackupLog[0] || event.completedFiles() == event.totalFiles()) {
                            log.accept("Backup progress: files=" + event.completedFiles() + "/" + event.totalFiles()
                                    + "; bytes=" + event.completedBytes() + "/" + event.totalBytes());
                            nextBackupLog[0] = now + TimeUnit.SECONDS.toNanos(5);
                        }
                    });
            log.accept("Mandatory backup completed: " + backup.archive().getFileName());
            leases.validate();
            var report = WorldProcessor.apply(plan, progress);
            leases.validate();
            if (config.runMode() == RunMode.ONCE && plan.summary().deferredChunks() == 0) {
                WorldPreparationState.markComplete(target);
            }
            log.accept("Offline preparation completed: prepared=" + report.preparedChunks()
                    + "; repaired=" + report.repairedChunks() + "; deferred=" + plan.summary().deferredChunks());
        }
    }

    private static void validateVersion(Path world, String version) throws IOException {
        try (var input = Files.newInputStream(world.resolve("level.dat"))) {
            var named = new NBTDeserializer(true).fromStream(input);
            if (!(named.getTag() instanceof CompoundTag root)) throw new IOException("Invalid level metadata");
            CompoundTag data = root.getCompoundTag("Data");
            CompoundTag recorded = data == null ? null : data.getCompoundTag("Version");
            if (recorded == null || !matchesVersion(version, recorded, data.getInt("DataVersion"))) {
                throw new IOException("Offline world must match the running Minecraft version exactly: " + world);
            }
        }
    }

    static boolean matchesVersion(String expected, CompoundTag recorded, int dataVersion) {
        if (expected.equals(recorded.getString("Name"))) return true;
        // Spigot's unobfuscated 1.21.11 build retains the stable release's data version.
        return "1.21.11".equals(expected)
                && "1.21.11 Unobfuscated".equals(recorded.getString("Name"))
                && dataVersion == 4671 && recorded.getInt("Id") == 4671
                && "main".equals(recorded.getString("Series"))
                && recorded.get("Snapshot") instanceof ByteTag && !recorded.getBoolean("Snapshot");
    }
}
