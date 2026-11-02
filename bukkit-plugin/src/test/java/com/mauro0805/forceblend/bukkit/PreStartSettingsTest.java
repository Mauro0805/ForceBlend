package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.ForceBlendConfigStore;
import com.mauro0805.forceblend.config.RunMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PreStartSettingsTest {
    @TempDir Path directory;

    @Test
    void defaultRenameRetainsLegacySettingsAndRejectsAmbiguity() throws Exception {
        var options = Map.of("--world-container", directory.toString());
        Path legacy = directory.resolve(PreStartSettings.LEGACY_FILE_NAME);
        Files.writeString(legacy, "accept-world-preparation=true\nworlds=legacy-world\n");
        assertEquals(List.of("legacy-world"), PreStartSettings.load(options, List.of()).worlds());
        Path current = directory.resolve(PreStartSettings.FILE_NAME);
        Files.writeString(current, "accept-world-preparation=false\nworlds=current-world\n");
        assertThrows(IOException.class, () -> PreStartSettings.load(options, List.of()));
        assertFalse(PreStartSettings.load(Map.of("--settings", current.toString()), List.of()).accepted());
        Files.delete(legacy);
        assertEquals(List.of("current-world"), PreStartSettings.load(options, List.of()).worlds());
    }

    @Test
    void explicitlyMissingSettingsFileIsAnErrorEvenIfProcessingWouldBeDisabled() {
        assertThrows(IOException.class, () -> PreStartSettings.load(Map.of("--settings",
                directory.resolve("missing.properties").toString(), "--automatic-processing", "false"), List.of()));
    }

    @Test
    void packagedDefaultsHaveAllModAndPluginControls() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/" + PreStartSettings.FILE_NAME)) {
            assertNotNull(input);
            properties.load(input);
        }
        assertTrue(properties.stringPropertyNames().containsAll(TargetConfiguration.PROCESSING_KEYS));
        for (String key : List.of("worlds", "analysis-enabled", "preparation-enabled", "accept-world-preparation")) {
            assertTrue(properties.containsKey(key), key);
        }
        assertEquals(ForceBlendConfig.DEFAULTS, TargetConfiguration.properties(properties));
        assertFalse(TargetConfiguration.flag(properties, "accept-world-preparation", true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "auto", "0", "1", "256", " ALL "})
    void sameThreadAliasesAsMod(String threads) throws Exception {
        String text = "automatic-processing=true\nautomatic-backup=true\ndetailed-logging=false\n"
                + "maximum-chunks-per-run=unlimited\nprocessing-threads=" + threads + "\nrun-mode=once\n";
        Path file = directory.resolve("mod.properties");
        Files.writeString(file, text);
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        var mod = new ForceBlendConfigStore(file).loadOrCreate();
        assertTrue(mod.warnings().isEmpty());
        assertEquals(mod.config(), TargetConfiguration.properties(properties));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unlimited", "0", "1", "2147483648", " UNLIMITED "})
    void sameChunkLimitAliasesAsMod(String limit) throws Exception {
        String text = "maximum-chunks-per-run=" + limit + "\nprocessing-threads=all\n";
        Path file = directory.resolve("mod.properties");
        Files.writeString(file, text);
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        var mod = new ForceBlendConfigStore(file).loadOrCreate();
        assertTrue(mod.warnings().isEmpty());
        assertEquals(mod.config(), TargetConfiguration.properties(properties));
    }

    @Test
    void settingsOnlyResolvesInputsFlagsAndAllSixProcessingValues() throws Exception {
        Path file = directory.resolve("forceblend-prestart.properties");
        Files.writeString(file, "world-container=.\nworlds=World A,World B\nminecraft-version=1.18.2\n"
                + "accept-world-preparation=TRUE\npreparation-enabled=false\nanalysis-enabled=true\n"
                + "automatic-processing=true\nautomatic-backup=false\ndetailed-logging=true\n"
                + "maximum-chunks-per-run=7\nprocessing-threads=2\nrun-mode=EVERY-OPEN\n");
        var loaded = PreStartSettings.load(Map.of("--settings", file.toString()), List.of());
        assertEquals(directory.toRealPath(), loaded.container());
        assertEquals(List.of("World A", "World B"), loaded.worlds());
        assertEquals("1.18.2", loaded.version());
        assertTrue(loaded.accepted());
        assertFalse(loaded.preparationEnabled());
        assertTrue(loaded.analysisEnabled());
        assertEquals(new ForceBlendConfig(true, true, true, 7, 2, RunMode.EVERY_OPEN), loaded.processing());
    }

    @Test
    void indexedWorldsPreserveCommasAndUnicodeAndCliOverridesSettings() throws Exception {
        Path file = directory.resolve("settings.properties");
        Files.writeString(file, "worlds.1=Água, 世界\nworlds.0=first\nminecraft-version=1.18.2\nprocessing-threads=all\n");
        assertEquals(List.of("first", "Água, 世界"), PreStartSettings.load(Map.of("--settings", file.toString()), List.of()).worlds());
        var override = PreStartSettings.load(Map.of("--settings", file.toString(), "--processing-threads", "2",
                "--minecraft-version", "1.21.11"), List.of("explicit"));
        assertEquals(List.of("explicit"), override.worlds());
        assertEquals(2, override.processing().processingThreads());
        assertEquals("1.21.11", override.version());
        Files.writeString(file, "worlds=first\nworlds.0=second\n");
        assertThrows(IllegalArgumentException.class, () -> PreStartSettings.load(Map.of("--settings", file.toString()), List.of()));
    }

    @Test
    void invalidSettingsFailClosedInsteadOfSilentlyIncreasingWork() throws Exception {
        Path file = directory.resolve("settings.properties");
        for (String text : List.of("automatic-backup=perhaps", "processing-threads=1.5", "maximum-chunks-per-run=-1",
                "run-mode=unknown", "analysis-enabled=yes", "preparation-enabled=yes", "accept-world-preparation=yes", "typo=true")) {
            Files.writeString(file, text);
            assertThrows(IllegalArgumentException.class, () -> PreStartSettings.load(Map.of("--settings", file.toString()), List.of()), text);
        }
    }

    @Test
    void yamlAliasesAndDefaultsUseSameServerPolicy() {
        assertEquals(ForceBlendConfig.DEFAULTS, TargetConfiguration.processing(Map.of()));
        assertEquals(ForceBlendConfig.DEFAULTS, TargetConfiguration.processing(Map.of(
                "maximum-chunks-per-run", "unlimited", "processing-threads", "auto", "automatic-backup", false)));
    }
}
