package com.mauro0805.forceblend.server;

import com.mauro0805.forceblend.WorldPreparer;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;

public final class ForceBlendServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDirectory = loader.getGameDir().toAbsolutePath().normalize();
        try {
            Path worldDirectory = ServerWorldPath.resolve(gameDirectory, loader.getLaunchArguments(false));
            String minecraftVersion = loader.getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElseThrow(() -> new IllegalStateException("Minecraft version metadata is unavailable"));
            WorldPreparer.prepare(
                worldDirectory.getFileName().toString(),
                worldDirectory,
                gameDirectory,
                minecraftVersion
            );
        } catch (IOException exception) {
            throw new IllegalStateException("ForceBlend could not safely prepare the server world", exception);
        }
    }
}
