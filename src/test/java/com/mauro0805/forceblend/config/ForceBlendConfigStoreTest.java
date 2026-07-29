package com.mauro0805.forceblend.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ForceBlendConfigStoreTest {
    @Test
    void createsDocumentedDefaultsWhenTheConfigurationIsMissing(@TempDir Path gameDirectory)
        throws IOException {
        Path configFile = ForceBlendConfigStore.defaultPath(gameDirectory);
        ForceBlendConfigStore store = new ForceBlendConfigStore(configFile);

        ConfigLoadResult result = store.loadOrCreate();

        assertEquals(ForceBlendConfig.DEFAULTS, result.config());
        assertTrue(result.warnings().isEmpty());
        assertTrue(Files.isRegularFile(configFile));
        String persisted = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(persisted.contains("automatic-processing=true"));
        assertTrue(persisted.contains("automatic-backup=false"));
        assertTrue(persisted.contains("detailed-logging=false"));
        assertTrue(persisted.contains("maximum-chunks-per-run=unlimited"));
        assertTrue(persisted.contains("processing-threads=all"));
    }

    @Test
    void savesAndLoadsEverySettingWithoutLeavingTemporaryFiles(@TempDir Path directory)
        throws IOException {
        Path configFile = directory.resolve("nested").resolve("forceblend.properties");
        ForceBlendConfigStore store = new ForceBlendConfigStore(configFile);
        ForceBlendConfig expected = new ForceBlendConfig(false, true, true, 5_000, 6);

        store.save(expected);
        ConfigLoadResult loaded = store.loadOrCreate();

        assertEquals(expected, loaded.config());
        assertTrue(loaded.warnings().isEmpty());
        try (var children = Files.list(configFile.getParent())) {
            assertEquals(1, children.count());
        }
    }

    @Test
    void recoversInvalidIndividualValuesAndReportsThem(@TempDir Path directory) throws IOException {
        Path configFile = directory.resolve("forceblend.properties");
        Files.writeString(
            configFile,
            "automatic-processing=maybe\n"
                + "automatic-backup=true\n"
                + "detailed-logging=FALSE\n"
                + "maximum-chunks-per-run=-12\n"
                + "processing-threads=9999\n",
            StandardCharsets.UTF_8
        );

        ConfigLoadResult result = new ForceBlendConfigStore(configFile).loadOrCreate();

        assertTrue(result.config().automaticProcessingEnabled());
        assertTrue(result.config().automaticBackupEnabled());
        assertFalse(result.config().detailedLoggingEnabled());
        assertEquals(ForceBlendConfig.UNLIMITED_CHUNKS, result.config().maximumChunksPerRun());
        assertEquals(ForceBlendConfig.ALL_AVAILABLE_THREADS, result.config().processingThreads());
        assertEquals(3, result.warnings().size());
    }

    @Test
    void resolvesAllOrExplicitThreadsWithoutOversubscribingTheJvm() {
        assertEquals(12, ForceBlendConfig.DEFAULTS.resolvedProcessingThreads(12));
        assertEquals(512, ForceBlendConfig.DEFAULTS.resolvedProcessingThreads(512));
        assertEquals(4, new ForceBlendConfig(true, false, false, 0, 8).resolvedProcessingThreads(4));
        assertEquals(3, new ForceBlendConfig(true, false, false, 0, 3).resolvedProcessingThreads(16));
        assertThrows(IllegalArgumentException.class, () -> ForceBlendConfig.DEFAULTS.resolvedProcessingThreads(0));
    }

    @Test
    void failedReplacementPreservesTheTargetAndCleansItsTemporaryFile(@TempDir Path directory)
        throws IOException {
        Path configFile = directory.resolve("forceblend.properties");
        Files.createDirectory(configFile);
        ForceBlendConfigStore store = new ForceBlendConfigStore(configFile);

        assertThrows(IOException.class, () -> store.save(ForceBlendConfig.DEFAULTS));
        assertTrue(Files.isDirectory(configFile));
        try (var children = Files.list(directory)) {
            assertEquals(1, children.count());
        }
    }
}
