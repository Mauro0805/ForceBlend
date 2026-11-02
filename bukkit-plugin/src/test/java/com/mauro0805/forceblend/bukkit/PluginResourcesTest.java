package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginResourcesTest {
    @Test
    void descriptorUsesConventionalStartupEntryPoint() throws IOException {
        String descriptor = resource("/plugin.yml");
        assertTrue(descriptor.contains("load: STARTUP"));
        assertTrue(descriptor.contains("main: com.mauro0805.forceblend.bukkit.ForceBlendPlugin"));
        assertTrue(descriptor.contains("api-version: '1.18'"));
        assertFalse(descriptor.contains("folia-supported: true"));
    }

    @Test
    void defaultsDoNotGuessWorldTargets() throws IOException {
        assertTrue(resource("/config.yml").contains("worlds: []"));
    }

    private String resource(String name) throws IOException {
        try (var input = getClass().getResourceAsStream(name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
