package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.config.ForceBlendConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

record PreStartSettings(Path container, List<String> worlds, String version, ForceBlendConfig processing,
                        boolean preparationEnabled, boolean analysisEnabled, boolean accepted) {
    static final String FILE_NAME = "forceblend-settings-and-acceptance.properties";
    static final String LEGACY_FILE_NAME = "forceblend-prestart.properties";

    static PreStartSettings load(Map<String, String> options, List<String> explicitWorlds) throws IOException {
        Path file = options.containsKey("--settings") ? Path.of(options.get("--settings"))
                : defaultFile(Path.of(options.getOrDefault("--world-container", ".")));
        file = file.toAbsolutePath().normalize();
        Properties properties = new Properties();
        if (options.containsKey("--settings") && !Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Explicit settings file does not exist: " + file);
        }
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Settings must be a regular, unlinked file: " + file);
            try (var input = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(input); }
        }
        for (String key : properties.stringPropertyNames()) {
            if (!TargetConfiguration.PROCESSING_KEYS.contains(key)
                    && !List.of("accept-world-preparation", "analysis-enabled", "preparation-enabled", "worlds",
                    "world-container", "minecraft-version").contains(key) && !key.matches("worlds\\.(0|[1-9][0-9]*)")) {
                throw new IllegalArgumentException("Unknown setting: " + key);
            }
        }
        for (var option : options.entrySet()) {
            String key = option.getKey();
            if (key.startsWith("--") && TargetConfiguration.PROCESSING_KEYS.contains(key.substring(2))) {
                properties.setProperty(key.substring(2), option.getValue());
            } else if (!List.of("--settings", "--world-container", "--minecraft-version", "--action").contains(key)) {
                throw new IllegalArgumentException("Unknown option: " + key);
            }
        }
        Path container = options.containsKey("--world-container") ? Path.of(options.get("--world-container"))
                : file.getParent().resolve(properties.getProperty("world-container", "."));
        List<String> worlds = explicitWorlds.isEmpty() ? worldNames(properties) : List.copyOf(explicitWorlds);
        return new PreStartSettings(container.toRealPath(), worlds,
                options.getOrDefault("--minecraft-version", properties.getProperty("minecraft-version", "")).strip(),
                TargetConfiguration.properties(properties), TargetConfiguration.flag(properties, "preparation-enabled", true),
                TargetConfiguration.flag(properties, "analysis-enabled", false),
                TargetConfiguration.flag(properties, "accept-world-preparation", false));
    }

    private static Path defaultFile(Path directory) throws IOException {
        Path current = directory.resolve(FILE_NAME);
        Path legacy = directory.resolve(LEGACY_FILE_NAME);
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Both settings filenames exist; keep one or select a file explicitly with --settings");
        }
        return Files.exists(current, LinkOption.NOFOLLOW_LINKS) ? current
                : Files.exists(legacy, LinkOption.NOFOLLOW_LINKS) ? legacy : current;
    }

    private static List<String> worldNames(Properties properties) {
        Map<Integer, String> indexed = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("worlds.")) indexed.put(Integer.parseInt(key.substring(7)), properties.getProperty(key));
        }
        String list = properties.getProperty("worlds", "");
        if (!indexed.isEmpty()) {
            if (!list.isBlank()) throw new IllegalArgumentException("Use worlds or worlds.N, not both");
            return List.copyOf(indexed.values());
        }
        return list.isBlank() ? List.of() : Arrays.stream(list.split(",", -1)).map(String::strip).toList();
    }
}
