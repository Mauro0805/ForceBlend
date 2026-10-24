package com.mauro0805.forceblend.server;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ServerWorldPath {
    private ServerWorldPath() {
    }

    public static Path resolve(Path gameDirectory, String[] arguments) throws IOException {
        Properties settings = new Properties();
        Path propertiesFile = gameDirectory.resolve("server.properties");
        if (Files.exists(propertiesFile)) {
            try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
                settings.load(reader);
            }
        }
        String levelName = settings.getProperty("level-name", "world");
        Path universe = gameDirectory;
        for (int index = 0; index < arguments.length; index++) {
            String argument = arguments[index];
            if (argument.equals("--world") || argument.equals("--universe")) {
                if (++index == arguments.length) {
                    throw new IOException("Missing value for server option " + argument);
                }
                if (argument.equals("--world")) {
                    levelName = arguments[index];
                } else {
                    universe = gameDirectory.resolve(arguments[index]);
                }
            } else if (argument.startsWith("--world=")) {
                levelName = argument.substring("--world=".length());
            } else if (argument.startsWith("--universe=")) {
                universe = gameDirectory.resolve(argument.substring("--universe=".length()));
            }
        }
        if (levelName.isBlank()) {
            throw new IOException("The server world name must not be empty");
        }
        Path normalizedUniverse = universe.toAbsolutePath().normalize();
        Path world = normalizedUniverse.resolve(levelName).normalize();
        if (!world.startsWith(normalizedUniverse) || world.equals(normalizedUniverse)) {
            throw new IOException("The server world resolves outside its universe directory: " + levelName);
        }
        return world;
    }
}
