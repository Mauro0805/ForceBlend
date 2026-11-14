package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import com.mauro0805.forceblend.config.ForceBlendConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TargetConfigurationTest {
    @Test
    void rejectsScalarAndMissingLists() {
        for (Object value : List.of(42, true, "world", Map.of("world", "path"))) {
            assertThrows(IllegalArgumentException.class, () -> TargetConfiguration.worldNames(value));
        }
        assertThrows(IllegalArgumentException.class, () -> TargetConfiguration.worldNames(null));
    }

    @Test
    void rejectsNonStringEntriesWithoutCoercion() {
        assertThrows(IllegalArgumentException.class, () -> TargetConfiguration.worldNames(List.of("world", 42)));
        List<Object> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> TargetConfiguration.worldNames(withNull));
    }

    @Test
    void preservesExplicitTargetsInAnImmutableCopy() {
        List<String> original = new ArrayList<>(List.of("World 世界", "second"));
        List<String> parsed = TargetConfiguration.worldNames(original);
        original.clear();
        assertEquals(List.of("World 世界", "second"), parsed);
        assertThrows(UnsupportedOperationException.class, () -> parsed.add("third"));
        assertEquals(List.of(), TargetConfiguration.worldNames(List.of()));
    }

    @Test
    void processingDefaultsMatchTheModServerDefaults() {
        var config = TargetConfiguration.processing(Map.of());
        assertTrue(config.automaticBackupEnabled());
        assertEquals(ForceBlendConfig.DEFAULTS, config);
        assertFalse(config.hasChunkLimit());
    }

    @Test
    void rejectsNumericCoercionAndUnsupportedRunModes() {
        for (Map<String, Object> values : List.<Map<String, Object>>of(
                Map.of("processing-threads", 1.5), Map.of("processing-threads", 257),
                Map.of("maximum-chunks-per-run", -1), Map.of("run-mode", "sometimes"),
                Map.of("automatic-processing", "true"))) {
            assertThrows(IllegalArgumentException.class, () -> TargetConfiguration.processing(values));
        }
    }
}
