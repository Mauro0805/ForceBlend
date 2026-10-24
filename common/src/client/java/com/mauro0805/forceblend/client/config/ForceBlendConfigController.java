package com.mauro0805.forceblend.client.config;

import com.mauro0805.forceblend.WorldPreparer;
import com.mauro0805.forceblend.client.ui.NativeScreen;
import com.mauro0805.forceblend.config.ConfigLoadResult;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.ForceBlendConfigStore;
import com.mauro0805.forceblend.config.RunMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.LongConsumer;

public final class ForceBlendConfigController {
    private ForceBlendConfigController() {
    }

    public static ForceBlendConfig load(Path gameDirectory) {
        ForceBlendConfigStore store = new ForceBlendConfigStore(ForceBlendConfigStore.defaultPath(gameDirectory));
        try {
            ConfigLoadResult result = store.loadOrCreate();
            result.warnings().forEach(warning -> WorldPreparer.LOGGER.warn("ForceBlend config: {}", warning));
            return result.config();
        } catch (IOException | RuntimeException exception) {
            WorldPreparer.LOGGER.error(
                "Could not load {}; using ForceBlend defaults for this session",
                store.configFile(),
                exception
            );
            return ForceBlendConfig.DEFAULTS;
        }
    }

    public static Object editor(Path gameDirectory, Object parent) {
        ForceBlendConfigStore store = new ForceBlendConfigStore(ForceBlendConfigStore.defaultPath(gameDirectory));
        ForceBlendConfig current;
        try {
            ConfigLoadResult result = store.loadOrCreate();
            result.warnings().forEach(warning -> WorldPreparer.LOGGER.warn("ForceBlend config: {}", warning));
            current = result.config();
        } catch (IOException | RuntimeException exception) {
            WorldPreparer.LOGGER.error("Could not load {}; opening defaults", store.configFile(), exception);
            current = ForceBlendConfig.DEFAULTS;
        }

        return new Settings(store, current, parent).screen();
    }

    private static final class Settings {
        private final ForceBlendConfigStore store;
        private final Object parent;
        private boolean automatic;
        private boolean backup;
        private boolean logging;
        private long limit;
        private int threads;
        private RunMode mode;

        Settings(ForceBlendConfigStore store, ForceBlendConfig config, Object parent) {
            this.store = store;
            this.parent = parent;
            automatic = config.automaticProcessingEnabled();
            backup = config.automaticBackupEnabled();
            logging = config.detailedLoggingEnabled();
            limit = config.maximumChunksPerRun();
            threads = config.processingThreads();
            mode = config.runMode();
        }

        Object screen() {
            NativeScreen screen = new NativeScreen("ForceBlend settings");
            screen.content(view -> {
                view.line("ForceBlend settings");
                view.button("Automatic analysis: " + (automatic ? "on" : "off"), () -> {
                    automatic = !automatic;
                    refresh();
                });
                view.button("Automatic backup: " + (backup ? "on" : "off"), () -> {
                    backup = !backup;
                    refresh();
                });
                view.button("Detailed logging: " + (logging ? "on" : "off"), () -> {
                    logging = !logging;
                    refresh();
                });
                view.button("Execution: " + mode, () -> {
                    mode = mode == RunMode.ONCE ? RunMode.EVERY_OPEN : RunMode.ONCE;
                    refresh();
                });
                view.button("Chunk limit: " + (limit == 0 ? "max" : limit), () ->
                    number("Chunk limit (0 = max)", limit, Long.MAX_VALUE, value -> limit = value));
                view.button("Processing threads: " + (threads == 0 ? "all" : threads), () ->
                    number("Threads (0 = all, max 256)", threads, ForceBlendConfig.MAX_CONFIGURED_THREADS,
                        value -> threads = (int) value));
                view.button("Save and return", this::save);
                view.button("Cancel", () -> NativeScreen.show(parent));
            }).onClose(() -> NativeScreen.show(parent));
            return screen.handle();
        }

        void refresh() {
            NativeScreen.show(screen());
        }

        void number(String label, long current, long maximum, LongConsumer accept) {
            NativeScreen screen = new NativeScreen(label);
            Object[] input = new Object[1];
            Object[] message = new Object[1];
            screen.content(view -> {
                view.line(label);
                input[0] = view.input(Long.toString(current));
                message[0] = view.line("Enter a nonnegative whole number");
                view.button("Done", () -> {
                    try {
                        long value = Long.parseLong(NativeScreen.value(input[0]).trim());
                        if (value < 0 || value > maximum) {
                            throw new NumberFormatException("Out of range");
                        }
                        accept.accept(value);
                        refresh();
                    } catch (NumberFormatException exception) {
                        view.label(message[0], "Enter a number from 0 to " + maximum);
                    }
                });
                view.button("Cancel", this::refresh);
            }).onClose(this::refresh);
            NativeScreen.show(screen.handle());
        }

        void save() {
            try {
                store.save(new ForceBlendConfig(automatic, backup, logging, limit, threads, mode));
                WorldPreparer.LOGGER.info("Saved ForceBlend settings to {}", store.configFile());
                NativeScreen.show(parent);
            } catch (IOException | RuntimeException exception) {
                WorldPreparer.LOGGER.error("Could not save ForceBlend settings to {}", store.configFile(), exception);
                NativeScreen error = new NativeScreen("ForceBlend settings failed");
                error.content(view -> {
                    view.line("Could not save settings; see latest.log");
                    view.button("Return to settings", this::refresh);
                }).onClose(this::refresh);
                NativeScreen.show(error.handle());
            }
        }
    }
}
