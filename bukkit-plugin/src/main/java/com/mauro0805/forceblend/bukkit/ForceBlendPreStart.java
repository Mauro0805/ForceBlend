package com.mauro0805.forceblend.bukkit;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Offline entry point; it never starts a Minecraft server. */
public final class ForceBlendPreStart {
    private ForceBlendPreStart() {}

    public static void main(String[] args) {
        System.exit(execute(args, System.out, System.err));
    }

    static int execute(String[] args, PrintStream output, PrintStream error) {
        if (args.length == 1 && "--help".equals(args[0])) {
            output.println("Stop the server first. Usage: java -jar ForceBlend-bukkit.jar [--settings <properties-file>]"
                    + " [--world-container <directory>] [--world <relative-world>] [--minecraft-version <exact-version>]"
                    + " [--action analyze|prepare|unchanged]. Processing settings may also be overridden with --<setting> <value>.");
            output.println("File acceptance is required. Full backups are mandatory; any nonzero exit must prevent server startup.");
            return 0;
        }
        try {
            List<String> worlds = new ArrayList<>();
            Map<String, String> options = new HashMap<>();
            for (int index = 0; index < args.length; index += 2) {
                if (index + 1 >= args.length) throw new IllegalArgumentException("Missing value for " + args[index]);
                String key = args[index];
                if ("--world".equals(key)) worlds.add(args[index + 1]);
                else if (options.putIfAbsent(key, args[index + 1]) != null) throw new IllegalArgumentException("Repeated option: " + key);
            }
            PreStartSettings settings = PreStartSettings.load(options, worlds);
            String action = options.getOrDefault("--action", settings.preparationEnabled() ? "prepare"
                    : settings.analysisEnabled() ? "analyze" : "unchanged");
            if (!List.of("analyze", "prepare", "unchanged").contains(action)) {
                throw new IllegalArgumentException("--action must be analyze, prepare or unchanged");
            }
            if (!settings.processing().automaticProcessingEnabled() || "unchanged".equals(action)
                    || ("prepare".equals(action) && !settings.preparationEnabled())) {
                output.println("Automatic preparation is disabled or unchanged entry selected; no world data changed.");
                return 0;
            }
            if (settings.worlds().isEmpty() || settings.version().isBlank()) {
                throw new IllegalArgumentException("Set worlds and minecraft-version in the settings file or use --world and --minecraft-version");
            }
            if ("prepare".equals(action) && !settings.accepted()) {
                throw new IllegalArgumentException("Set accept-world-preparation=true after reading the backup and stopped-server requirements");
            }
            var targets = WorldTargets.validate(settings.container(), settings.worlds());
            Logger logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { output.println(record.getMessage()); }
                @Override public void flush() { output.flush(); }
                @Override public void close() { output.flush(); }
            });
            if ("analyze".equals(action)) {
                OfflinePreparation.run(targets, settings.container(), settings.version(), settings.processing(), logger, true);
                output.println("Read-only analysis completed; no backup, terrain or completion changes were made.");
            } else {
                OfflinePreparation.run(targets, settings.container(), settings.version(), settings.processing(), logger);
                output.println("Pre-start preparation succeeded. Start the server normally; keep other editors closed.");
            }
            return 0;
        } catch (IOException | IllegalArgumentException exception) {
            error.println("Pre-start preparation failed: " + exception.getMessage());
            error.println("Do not start the server automatically. Inspect retained backups before retrying.");
            return 1;
        }
    }
}
