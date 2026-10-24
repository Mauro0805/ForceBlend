package com.mauro0805.forceblend.client;

import com.mauro0805.forceblend.WorldPreparer;
import com.mauro0805.forceblend.backup.BackupProgress;
import com.mauro0805.forceblend.client.config.ForceBlendConfigController;
import com.mauro0805.forceblend.client.ui.NativeScreen;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.processing.ChunkProcessingEvent;
import com.mauro0805.forceblend.processing.ProcessingPlan;
import com.mauro0805.forceblend.processing.ProcessingPlanSummary;
import com.mauro0805.forceblend.processing.ProcessingProgress;
import com.mauro0805.forceblend.processing.ProcessingProgressListener;
import com.mauro0805.forceblend.processing.ProcessingRegionEvent;
import com.mauro0805.forceblend.processing.ProcessingReport;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Suspends world opening while native screens and background preparation run. */
public final class WorldOpenWorkflow {
    private static final ThreadLocal<Boolean> RESUMING = ThreadLocal.withInitial(() -> false);
    private static final AtomicBoolean ACTIVE = new AtomicBoolean();

    private WorldOpenWorkflow() {
    }

    /** @return whether the original world-open invocation must be cancelled */
    public static boolean intercept(
        Object owner, String methodName, Object[] arguments, String levelId,
        Path worldDirectory, Path gameDirectory, String minecraftVersion, Runnable onCancel
    ) {
        if (RESUMING.get()) {
            return false;
        }
        ForceBlendConfig config = ForceBlendConfigController.load(gameDirectory);
        try {
            if (WorldPreparer.shouldSkip(worldDirectory, config)) {
                WorldPreparer.LOGGER.info("World '{}' is already prepared; once-per-world mode skips it", levelId);
                return false;
            }
            if (!ACTIVE.compareAndSet(false, true)) {
                return true;
            }
            new Session(owner, methodName, arguments, levelId, worldDirectory,
                gameDirectory, minecraftVersion, onCancel, config).start();
        } catch (IOException | RuntimeException | LinkageError exception) {
            ACTIVE.set(false);
            WorldPreparer.LOGGER.error("World '{}' was not opened: ForceBlend could not start. No preparation was applied.",
                levelId, exception);
            onCancel.run();
        }
        return true;
    }

    private static final class Session {
        private final Object owner;
        private final Method open;
        private final Object[] arguments;
        private final String levelId;
        private final Path world;
        private final Path game;
        private final String version;
        private final Runnable cancel;
        private final ForceBlendConfig config;
        private final Executor client = (Executor) NativeScreen.client();
        private final AtomicReference<Progress> progress = new AtomicReference<>(
            new Progress("Analyzing terrain borders...", "Preparing analysis", "ETA: estimating...", 0));
        private boolean finished;
        private long backupStartedNanos;

        Session(Object owner, String methodName, Object[] arguments, String levelId, Path world,
                Path game, String version, Runnable cancel, ForceBlendConfig config) {
            this.owner = owner;
            this.arguments = arguments.clone();
            this.levelId = levelId;
            this.world = world;
            this.game = game;
            this.version = version;
            this.cancel = cancel;
            this.config = config;
            open = findOpenMethod(owner.getClass(), methodName, arguments.length);
        }

        void start() {
            if (config.automaticProcessingEnabled()) {
                analyze();
                return;
            }
            NativeScreen screen = new NativeScreen("ForceBlend");
            screen.content(view -> {
                view.line("ForceBlend: " + levelId);
                view.button("Analyze terrain borders", this::analyze);
                view.button("Open without changes", this::resume);
                view.button("Cancel", this::cancel);
            }).onClose(this::cancel);
            NativeScreen.show(screen.handle());
        }

        void analyze() {
            showProgress();
            background(() -> {
                ProcessingPlan plan = WorldPreparer.analyze(levelId, world, version, config, listener());
                dispatch(() -> choose(plan));
            });
        }

        void choose(ProcessingPlan plan) {
            ProcessingPlanSummary summary = plan.summary();
            NativeScreen screen = new NativeScreen("ForceBlend");
            if (summary.selectedChunks() == 0) {
                screen.content(view -> {
                    view.line("ForceBlend: no changes needed");
                    view.line(summary.discoveredChunks() + " chunks scanned");
                    view.button("Open world", this::resume);
                    view.button("Cancel", this::cancel);
                }).onClose(this::cancel);
            } else {
                Duration estimate = plan.estimatedApplyDuration().orElseGet(() -> fallbackEstimate(summary));
                screen.content(view -> {
                    view.line("ForceBlend: " + levelId);
                    view.line(summary.selectedChunks() + " chunks to prepare / " + summary.affectedRegions() + " regions");
                    view.line(summary.discoveredChunks() + " chunks scanned; " + summary.affectedChunks() + " affected");
                    view.line(summary.deferredChunks() + " chunks deferred by the configured limit");
                    view.line("Estimated processing: " + duration(estimate) + " (backup excluded)");
                    // Players tend to take the first button without reading, so that one always backs up.
                    if (config.automaticBackupEnabled()) {
                        view.button("Prepare world (backup is on)", () -> apply(plan, true));
                    } else {
                        view.button("Back up and prepare world", () -> apply(plan, true));
                        view.button("Prepare without backup (risky)", () -> apply(plan, false));
                    }
                    view.button("Open without changes", this::resume);
                    view.button("Cancel", this::cancel);
                }).onClose(this::cancel);
            }
            NativeScreen.show(screen.handle());
        }

        void apply(ProcessingPlan plan, boolean backup) {
            backupStartedNanos = System.nanoTime();
            progress.set(new Progress(backup || config.automaticBackupEnabled() ? "Backing up world..." : "Preparing terrain...",
                "Waiting for processing", "ETA: estimating...", 0));
            showProgress();
            background(() -> {
                ProcessingReport report = WorldPreparer.apply(plan, world, game, config, backup,
                    this::backupProgress, listener());
                WorldPreparer.LOGGER.info("ForceBlend completed '{}': {} chunks prepared, {} repaired, {} deferred",
                    levelId, report.preparedChunks(), report.repairedChunks(), plan.summary().deferredChunks());
                dispatch(() -> {
                    long enterAt = System.nanoTime() + 1_500_000_000L;
                    NativeScreen complete = new NativeScreen("ForceBlend complete");
                    AtomicBoolean entering = new AtomicBoolean();
                    complete.content(view -> {
                        view.line("ForceBlend preparation complete");
                        view.line((report.preparedChunks() + report.repairedChunks()) + " chunks prepared");
                        view.line("Entering world...");
                    }).onTick(() -> {
                        if (System.nanoTime() >= enterAt && entering.compareAndSet(false, true)) {
                            NativeScreen.enqueue(() -> dispatch(this::resume));
                        }
                    });
                    NativeScreen.show(complete.handle());
                });
            });
        }

        void showProgress() {
            NativeScreen screen = new NativeScreen("ForceBlend processing");
            AtomicReference<List<Object>> widgets = new AtomicReference<>(List.of());
            screen.content(view -> widgets.set(List.of(view.line("ForceBlend: " + levelId),
                view.line("Starting..."), view.line(""), view.line(""), view.line(""),
                view.line("Keep Minecraft open until preparation finishes."))));
            screen.onTick(() -> {
                Progress snapshot = progress.get();
                List<Object> current = widgets.get();
                if (current.size() == 6) {
                    screen.label(current.get(1), snapshot.phase());
                    screen.label(current.get(2), bar(snapshot.fraction()));
                    screen.label(current.get(3), snapshot.detail());
                    screen.label(current.get(4), snapshot.eta());
                }
            });
            // No cancellation during writes: region files must finish their atomic replacement.
            NativeScreen.show(screen.handle());
        }

        ProcessingProgressListener listener() {
            return new ProcessingProgressListener() {
                @Override
                public void onProgress(ProcessingProgress update) {
                    String active = String.join(", ", update.activeRegions().stream().limit(2).toList());
                    double seconds = Math.max(0.001, update.elapsed().toMillis() / 1_000.0);
                    long speed = Math.round(update.completedChunks() / seconds);
                    String eta = "ETA: " + update.estimatedRemaining().map(WorldOpenWorkflow::duration).orElse("estimating...");
                    progress.set(new Progress(update.phase().toString().replace('_', ' ') + ": "
                            + update.completedChunks() + " chunks",
                        update.completedRegions() + "/" + update.totalRegions() + " regions; " + active,
                        speed > 0 ? eta + "; " + speed + " chunks/s" : eta,
                        update.completionFraction()));
                }

                @Override
                public boolean detailedEventsEnabled() {
                    return config.detailedLoggingEnabled();
                }

                @Override
                public void onRegionEvent(ProcessingRegionEvent event) {
                    WorldPreparer.LOGGER.info("ForceBlend '{}': {} {} {} ({} chunks)", levelId,
                        event.phase(), event.state(), event.regionFile(), event.chunks());
                }

                @Override
                public void onChunkEvent(ChunkProcessingEvent event) {
                    WorldPreparer.LOGGER.info("ForceBlend '{}': {} chunk [{}, {}] {}", levelId,
                        event.phase(), event.chunkX(), event.chunkZ(), event.outcome());
                }
            };
        }

        void backupProgress(BackupProgress update) {
            double fraction = update.totalBytes() == 0 ? 0 : (double) update.completedBytes() / update.totalBytes();
            String eta = "estimating...";
            if (fraction > 0) {
                double remaining = (System.nanoTime() - backupStartedNanos) * (1 - fraction) / fraction;
                eta = duration(Duration.ofNanos((long) Math.min(Long.MAX_VALUE, remaining)));
            }
            progress.set(new Progress("Backing up world...", update.completedFiles() + "/" + update.totalFiles()
                + " files: " + update.currentEntry(), "Backup ETA: " + eta, fraction));
        }

        void background(IoTask action) {
            Thread worker = new Thread(() -> {
                try {
                    action.run();
                } catch (IOException | RuntimeException exception) {
                    WorldPreparer.LOGGER.error("ForceBlend could not prepare world '{}'", levelId, exception);
                    dispatch(() -> failure(exception));
                }
            }, "ForceBlend preparation");
            worker.setDaemon(true);
            worker.start();
        }

        void dispatch(Runnable action) {
            client.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException | LinkageError exception) {
                    WorldPreparer.LOGGER.error("ForceBlend could not continue its native UI for '{}'; opening was cancelled",
                        levelId, exception);
                    cancel();
                }
            });
        }

        void failure(Exception exception) {
            NativeScreen screen = new NativeScreen("ForceBlend failed");
            screen.content(view -> {
                view.line("ForceBlend could not prepare this world");
                view.line(exception.getClass().getSimpleName() + ": see latest.log for details");
                view.line("The world has not been opened.");
                view.button("Return to world selection", this::cancel);
            }).onClose(this::cancel);
            NativeScreen.show(screen.handle());
        }

        void cancel() {
            if (!finished) {
                finished = true;
                ACTIVE.set(false);
                cancel.run();
            }
        }

        void resume() {
            if (finished) {
                return;
            }
            finished = true;
            ACTIVE.set(false);
            resumeWorldOpen(owner, open, arguments);
        }
    }

    static void resumeWorldOpen(Object owner, Method method, Object[] arguments) {
        RESUMING.set(true);
        try {
            method.invoke(owner, arguments);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot resume Minecraft world opening", exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Minecraft world opening failed", cause);
        } finally {
            RESUMING.remove();
        }
    }

    private static Method findOpenMethod(Class<?> owner, String name, int arguments) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == arguments) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        throw new IllegalStateException("Minecraft world-open method is unavailable: " + name);
    }

    private static Duration fallbackEstimate(ProcessingPlanSummary summary) {
        long chunks = summary.selectedChunks() * 1_000L / 3_000L;
        long bytes = summary.selectedRegionBytes() / (150L * 1_024L);
        return Duration.ofMillis(Math.max(1_000L, Math.max(chunks, bytes)));
    }

    private static String duration(Duration duration) {
        long seconds = Math.max(1, duration.toSeconds());
        return seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
    }

    private static String bar(double fraction) {
        double bounded = Math.max(0, Math.min(1, fraction));
        int filled = (int) (bounded * 24);
        return "[" + "|".repeat(filled) + ".".repeat(24 - filled) + "] " + (int) (bounded * 100) + "%";
    }

    private record Progress(String phase, String detail, String eta, double fraction) { }

    @FunctionalInterface
    private interface IoTask {
        void run() throws IOException;
    }
}
