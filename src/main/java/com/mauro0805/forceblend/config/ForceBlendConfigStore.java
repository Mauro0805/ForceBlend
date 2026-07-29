package com.mauro0805.forceblend.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Persists ForceBlend settings in a small, human-editable properties file.
 */
public record ForceBlendConfigStore(Path configFile) {
    private static final String FILE_NAME = "forceblend.properties";
    private static final String AUTOMATIC_PROCESSING = "automatic-processing";
    private static final String AUTOMATIC_BACKUP = "automatic-backup";
    private static final String DETAILED_LOGGING = "detailed-logging";
    private static final String MAXIMUM_CHUNKS = "maximum-chunks-per-run";
    private static final String PROCESSING_THREADS = "processing-threads";

    public ForceBlendConfigStore(Path configFile) {
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        if (this.configFile.getFileName() == null) {
            throw new IllegalArgumentException("The configuration path must point to a file");
        }
    }

    public static Path defaultPath(Path gameDirectory) {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        return gameDirectory.toAbsolutePath().normalize().resolve("config").resolve(FILE_NAME);
    }

    private static String serialize(ForceBlendConfig config) {
        return "# ForceBlend settings\n"
            + "# Use 'unlimited' for no per-run chunk limit and 'all' for every available processor.\n"
            + AUTOMATIC_PROCESSING + "=" + config.automaticProcessingEnabled() + "\n"
            + AUTOMATIC_BACKUP + "=" + config.automaticBackupEnabled() + "\n"
            + DETAILED_LOGGING + "=" + config.detailedLoggingEnabled() + "\n"
            + MAXIMUM_CHUNKS + "=" + (config.hasChunkLimit() ? config.maximumChunksPerRun() : "unlimited") + "\n"
            + PROCESSING_THREADS + "=" + (config.usesAllAvailableThreads() ? "all" : config.processingThreads()) + "\n";
    }

    private static boolean readBoolean(
        Properties properties,
        String key,
        boolean defaultValue,
        List<String> warnings
    ) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) {
            return defaultValue;
        }

        String normalizedValue = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalizedValue.equals("true")) {
            return true;
        }
        if (normalizedValue.equals("false")) {
            return false;
        }
        warnings.add("Invalid value for '" + key + "': '" + rawValue + "'; using " + defaultValue);
        return defaultValue;
    }

    private static long readChunkLimit(Properties properties, long defaultValue, List<String> warnings) {
        String rawValue = properties.getProperty(MAXIMUM_CHUNKS);
        if (rawValue == null) {
            return defaultValue;
        }

        String normalizedValue = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalizedValue.equals("unlimited") || normalizedValue.equals("0")) {
            return ForceBlendConfig.UNLIMITED_CHUNKS;
        }
        try {
            long parsedValue = Long.parseLong(normalizedValue);
            if (parsedValue > 0) {
                return parsedValue;
            }
        } catch (NumberFormatException ignored) {
            // The warning below includes the original value and the applied fallback.
        }

        warnings.add("Invalid value for '" + MAXIMUM_CHUNKS + "': '" + rawValue + "'; using unlimited");
        return defaultValue;
    }

    private static int readThreadCount(Properties properties, int defaultValue, List<String> warnings) {
        String rawValue = properties.getProperty(PROCESSING_THREADS);
        if (rawValue == null) {
            return defaultValue;
        }

        String normalizedValue = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalizedValue.equals("all") || normalizedValue.equals("auto") || normalizedValue.equals("0")) {
            return ForceBlendConfig.ALL_AVAILABLE_THREADS;
        }
        try {
            int parsedValue = Integer.parseInt(normalizedValue);
            if (parsedValue >= 1 && parsedValue <= ForceBlendConfig.MAX_CONFIGURED_THREADS) {
                return parsedValue;
            }
        } catch (NumberFormatException ignored) {
            // The warning below includes the original value and the applied fallback.
        }

        warnings.add("Invalid value for '" + PROCESSING_THREADS + "': '" + rawValue + "'; using all");
        return defaultValue;
    }

    private static void rejectSymbolicLink(Path path, String description) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("A symbolic link cannot be used as the ForceBlend " + description + ": " + path);
        }
    }

    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Loads the current settings. A missing file is initialized with documented defaults.
     * Invalid individual values fall back to their defaults and are reported as warnings.
     */
    public synchronized ConfigLoadResult loadOrCreate() throws IOException {
        if (!Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            save(ForceBlendConfig.DEFAULTS);
            return new ConfigLoadResult(ForceBlendConfig.DEFAULTS, List.of());
        }
        rejectSymbolicLink(configFile, "configuration file");
        if (!Files.isRegularFile(configFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The ForceBlend configuration path is not a regular file: " + configFile);
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        List<String> warnings = new ArrayList<>();
        ForceBlendConfig defaults = ForceBlendConfig.DEFAULTS;
        ForceBlendConfig config = new ForceBlendConfig(
            readBoolean(properties, AUTOMATIC_PROCESSING, defaults.automaticProcessingEnabled(), warnings),
            readBoolean(properties, AUTOMATIC_BACKUP, defaults.automaticBackupEnabled(), warnings),
            readBoolean(properties, DETAILED_LOGGING, defaults.detailedLoggingEnabled(), warnings),
            readChunkLimit(properties, defaults.maximumChunksPerRun(), warnings),
            readThreadCount(properties, defaults.processingThreads(), warnings)
        );
        return new ConfigLoadResult(config, warnings);
    }

    /**
     * Writes through a temporary sibling and atomically replaces the previous file when supported.
     */
    public synchronized void save(ForceBlendConfig config) throws IOException {
        Objects.requireNonNull(config, "config");
        Path parent = configFile.getParent();
        if (parent == null) {
            throw new IOException("The configuration path has no parent directory: " + configFile);
        }

        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(parent, "configuration directory");
            if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("The ForceBlend configuration parent is not a directory: " + parent);
            }
        } else {
            Files.createDirectories(parent);
        }
        if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(configFile, "configuration file");
        }

        byte[] encoded = serialize(config).getBytes(StandardCharsets.UTF_8);
        Path temporaryFile = Files.createTempFile(parent, ".forceblend-config-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(
                temporaryFile,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(encoded);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }

            if (Files.exists(configFile, LinkOption.NOFOLLOW_LINKS)) {
                rejectSymbolicLink(configFile, "configuration file");
            }
            replaceAtomically(temporaryFile, configFile);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }
}
