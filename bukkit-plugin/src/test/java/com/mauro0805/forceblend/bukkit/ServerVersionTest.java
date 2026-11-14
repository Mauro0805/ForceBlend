package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServerVersionTest {
    @Test
    void supportedReleaseFamiliesSurviveLegacyAndPaperApiForms() {
        String[] releases = {"1.18", "1.18.1", "1.18.2", "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
                "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6", "1.21", "1.21.1",
                "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9",
                "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2", "26.2", "26.3"};
        for (String release : releases) {
            for (String suffix : new String[]{"", "-R0.1-SNAPSHOT", ".build.29-alpha", ".build.237-stable"}) {
                String normalized = BukkitVersion.minecraftVersion(release + suffix);
                assertEquals(release, normalized);
                assertEquals(SupportedMinecraftVersions.profileFor(release),
                        SupportedMinecraftVersions.profileFor(normalized));
            }
        }
    }

    @Test
    void normalizationDoesNotExpandUnsupportedReleaseBoundaries() {
        for (String release : new String[]{"1.17.1", "1.18.3", "1.20.7", "1.21.12", "26.2.1", "26.4", "27.1"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SupportedMinecraftVersions.profileFor(BukkitVersion.minecraftVersion(release + ".build.1-stable")));
        }
    }

    @Test
    void acceptsLegacyAndBuildQualifiedApiVersions() {
        assertEquals("1.18", BukkitVersion.minecraftVersion("1.18-R0.1-SNAPSHOT"));
        assertEquals("1.18.2", BukkitVersion.minecraftVersion("1.18.2-R0.1-SNAPSHOT"));
        assertEquals("26.1.2", BukkitVersion.minecraftVersion("26.1.2-R0.1-SNAPSHOT"));
        assertEquals("26.3", BukkitVersion.minecraftVersion("26.3.build.237-stable"));
        assertEquals("26.1.2", BukkitVersion.minecraftVersion("26.1.2.build.74-stable"));
    }

    @Test
    void malformedBuildQualifiersRemainUnsupported() {
        for (String value : new String[]{"26.3.build.bad-stable", "26.3.build.-stable", "27.1.build.1-stable"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> SupportedMinecraftVersions.profileFor(BukkitVersion.minecraftVersion(value)));
        }
    }
}
