package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.RunMode;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class TargetConfiguration {
    static final List<String> PROCESSING_KEYS = List.of("automatic-processing", "automatic-backup", "detailed-logging",
            "maximum-chunks-per-run", "processing-threads", "run-mode");
    private TargetConfiguration() {}

    static List<String> worldNames(Object value) {
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("worlds must be a YAML list of directory names");
        }
        for (Object entry : entries) {
            if (!(entry instanceof String)) {
                throw new IllegalArgumentException("Every worlds entry must be a string");
            }
        }
        return entries.stream().map(String.class::cast).toList();
    }

    static ForceBlendConfig processing(Map<String, Object> values) {
        Object automatic = values.getOrDefault("automatic-processing", true);
        Object detailed = values.getOrDefault("detailed-logging", false);
        Object backup = values.getOrDefault("automatic-backup", true);
        if (!(automatic instanceof Boolean enabled) || !(detailed instanceof Boolean logging) || !(backup instanceof Boolean)) {
            throw new IllegalArgumentException("Processing and logging flags must be YAML booleans");
        }
        Object maximumValue = values.getOrDefault("maximum-chunks-per-run", ForceBlendConfig.DEFAULTS.maximumChunksPerRun());
        Object threadValue = values.getOrDefault("processing-threads", ForceBlendConfig.DEFAULTS.processingThreads());
        long maximum = "unlimited".equals(normalize(maximumValue)) ? 0 : integer(maximumValue, "maximum-chunks-per-run");
        long threads = List.of("all", "auto").contains(normalize(threadValue)) ? 0 : integer(threadValue, "processing-threads");
        if (threads < 0 || threads > ForceBlendConfig.MAX_CONFIGURED_THREADS) {
            throw new IllegalArgumentException("processing-threads must be between 0 and 256");
        }
        Object mode = normalize(values.getOrDefault("run-mode", "once"));
        RunMode runMode;
        if ("once".equals(mode)) runMode = RunMode.ONCE;
        else if ("every-open".equals(mode)) runMode = RunMode.EVERY_OPEN;
        else throw new IllegalArgumentException("run-mode must be once or every-open");
        return new ForceBlendConfig(enabled, true, logging, maximum, (int) threads, runMode);
    }

    static ForceBlendConfig properties(Properties properties) {
        Map<String, Object> values = new HashMap<>();
        for (String key : PROCESSING_KEYS) {
            if (!properties.containsKey(key)) continue;
            String value = properties.getProperty(key).strip().toLowerCase(Locale.ROOT);
            switch (key) {
                case "automatic-processing", "automatic-backup", "detailed-logging" -> values.put(key, flag(properties, key, true));
                case "maximum-chunks-per-run" -> values.put(key, "unlimited".equals(value) ? 0L : Long.parseLong(value));
                case "processing-threads" -> values.put(key, List.of("all", "auto").contains(value) ? 0L : Long.parseLong(value));
                case "run-mode" -> values.put(key, value);
                default -> throw new IllegalArgumentException("Unsupported processing key: " + key);
            }
        }
        return processing(values);
    }

    static boolean flag(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null) return fallback;
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        if (!List.of("true", "false").contains(normalized)) throw new IllegalArgumentException(key + " must be true or false");
        return Boolean.parseBoolean(normalized);
    }

    private static String normalize(Object value) {
        return value instanceof String text ? text.strip().toLowerCase(Locale.ROOT) : "";
    }

    private static long integer(Object value, String name) {
        if (!(value instanceof Integer || value instanceof Long)) {
            throw new IllegalArgumentException(name + " must be a YAML integer");
        }
        return ((Number) value).longValue();
    }
}
