package com.mauro0805.forceblend.neoforge;

import com.mauro0805.forceblend.WorldPreparer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.nio.file.Path;

@Mod("forceblend")
public final class ForceBlendNeoForge {
    public ForceBlendNeoForge() {
        NeoForge.EVENT_BUS.addListener(ForceBlendNeoForge::beforeServerStart);
    }

    private static void beforeServerStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        // Integrated worlds were already offered the cancellable client confirmation.
        if (!server.isDedicatedServer()) {
            return;
        }
        Path worldDirectory = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path gameDirectory = FMLPaths.GAMEDIR.get().toAbsolutePath().normalize();
        try {
            WorldPreparer.prepare(
                worldDirectory.getFileName().toString(),
                worldDirectory,
                gameDirectory,
                MinecraftVersion.name()
            );
        } catch (IOException exception) {
            throw new IllegalStateException("ForceBlend could not safely prepare the server world", exception);
        }
    }
}
