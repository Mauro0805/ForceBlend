package com.mauro0805.forceblend.bukkit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemporaryCleanupTest {
    private static Path previous;

    @Test @Order(1)
    void recordsAnIsolatedFixture(@TempDir Path directory) throws IOException {
        previous = directory;
        Files.writeString(directory.resolve("canary"), "fixture");
        assertTrue(Files.isRegularFile(directory.resolve("canary")));
    }

    @Test @Order(2)
    void previousFixtureWasActuallyRemoved() {
        assertNotNull(previous);
        assertFalse(Files.exists(previous), "Mandatory cleanup must finish between tests");
    }
}
