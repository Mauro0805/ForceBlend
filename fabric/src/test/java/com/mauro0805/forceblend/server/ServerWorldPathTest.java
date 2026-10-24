package com.mauro0805.forceblend.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ServerWorldPathTest {
    @Test
    void usesTheConfiguredLevelName(@TempDir Path game) throws IOException {
        Files.writeString(game.resolve("server.properties"), "level-name=Community World\n");
        assertEquals(game.resolve("Community World"), ServerWorldPath.resolve(game, new String[0]));
    }

    @Test
    void launchOptionsOverrideServerProperties(@TempDir Path game) throws IOException {
        Files.writeString(game.resolve("server.properties"), "level-name=unused\n");
        assertEquals(
            game.resolve("other-saves/chosen"),
            ServerWorldPath.resolve(game, new String[]{"--universe", "other-saves", "--world", "chosen"})
        );
        assertEquals(
            game.resolve("other-saves/chosen"),
            ServerWorldPath.resolve(game, new String[]{"--universe=other-saves", "--world=chosen"})
        );
    }

    @Test
    void rejectsMissingOrEscapingWorldNames(@TempDir Path game) throws IOException {
        assertEquals(game.resolve("world"), ServerWorldPath.resolve(game, new String[0]));
        assertThrows(IOException.class, () -> ServerWorldPath.resolve(game, new String[]{"--world", "../outside"}));
        assertThrows(IOException.class, () -> ServerWorldPath.resolve(game, new String[]{"--world", "."}));
        assertThrows(IOException.class, () -> ServerWorldPath.resolve(game, new String[]{"--world", ""}));
        assertThrows(IOException.class, () -> ServerWorldPath.resolve(game, new String[]{"--world"}));
    }
}
