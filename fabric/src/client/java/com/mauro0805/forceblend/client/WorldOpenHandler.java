package com.mauro0805.forceblend.client;

import net.fabricmc.loader.api.FabricLoader;
import com.mauro0805.forceblend.client.ui.NativeScreen;

import java.nio.file.Path;

public final class WorldOpenHandler {
    private WorldOpenHandler() {
    }

    public static boolean intercept(Object owner, String method, Object[] arguments, String levelId, Runnable onCancel) {
        FabricLoader loader = FabricLoader.getInstance();
        Path gameDirectory = loader.getGameDir().toAbsolutePath().normalize();
        Path savesDirectory = gameDirectory.resolve("saves");
        Path worldDirectory = savesDirectory.resolve(levelId).normalize();
        if (!worldDirectory.startsWith(savesDirectory) || worldDirectory.equals(savesDirectory)) {
            throw new IllegalArgumentException("World id resolves outside a world save: " + levelId);
        }
        String minecraftVersion = loader.getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Minecraft version metadata is unavailable"));
        return WorldOpenWorkflow.intercept(owner, method, arguments, levelId,
            worldDirectory, gameDirectory, minecraftVersion, onCancel);
    }

    public static Runnable returnToCurrentScreen() {
        Object parent = NativeScreen.current();
        return () -> NativeScreen.show(parent);
    }
}
