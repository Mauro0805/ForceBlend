package com.mauro0805.forceblend.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;

import com.mauro0805.forceblend.processing.ProcessingOptions;
import com.mauro0805.forceblend.processing.WorldProcessor;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;

public final class ForceBlendPlugin extends JavaPlugin implements Listener {
    @Override
    public void onLoad() {
        getLogger().info("Lifecycle: onLoad");
    }

    @Override
    public void onEnable() {
        getLogger().info("Lifecycle: onEnable; loaded worlds=" + getServer().getWorlds().size());
        if (!getServer().getWorlds().isEmpty()) {
            getLogger().severe("Diagnostic refused: worlds already loaded. Use a full server restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        boolean preparationAttempted = false;
        try {
            List<Path> targets = WorldTargets.validate(getServer().getWorldContainer().toPath(),
                    TargetConfiguration.worldNames(getConfig().get("worlds")));
            for (Path target : targets) getLogger().info("Validated directory: " + target);
            Object analysis = getConfig().get("analysis-enabled");
            if (!(analysis instanceof Boolean enabled)) {
                throw new IllegalArgumentException("analysis-enabled must be a YAML boolean");
            }
            Object preparation = getConfig().get("preparation-enabled");
            if (!(preparation instanceof Boolean prepare)) {
                throw new IllegalArgumentException("preparation-enabled must be a YAML boolean");
            }
            var processing = TargetConfiguration.processing(getConfig().getValues(false));
            if (!processing.automaticProcessingEnabled()) {
                getLogger().info("Automatic processing is disabled; skipping startup work");
            } else if (prepare) {
                if (!getServer().isPrimaryThread()) throw new IOException("Preparation requires the startup thread");
                for (var plugin : getServer().getPluginManager().getPlugins()) {
                    if (plugin != this) throw new IOException("Offline preparation preview requires ForceBlend as the only plugin");
                }
                if (!List.of("Spigot", "CraftBukkit", "Paper", "Purpur").contains(getServer().getName())) {
                    throw new IOException("Unverified server implementation; offline preparation refused");
                }
                preparationAttempted = true;
                OfflinePreparation.run(targets, getServer().getWorldContainer().toPath(),
                        getServer().getBukkitVersion().split("-", 2)[0], processing, getLogger());
            } else if (enabled) {
                String version = getServer().getBukkitVersion().split("-", 2)[0];
                var profile = SupportedMinecraftVersions.profileFor(version);
                for (Path target : targets) {
                    Path terrain = WorldTargets.terrainDirectory(target);
                    var plan = WorldProcessor.analyze(target.getFileName().toString(), terrain, profile,
                            new ProcessingOptions(processing.resolvedProcessingThreads(Runtime.getRuntime().availableProcessors()),
                                    processing.hasChunkLimit() ? (int) Math.min(Integer.MAX_VALUE, processing.maximumChunksPerRun())
                                            : ProcessingOptions.UNLIMITED_CHUNKS));
                    getLogger().info("Read-only blending analysis: discovered=" + plan.summary().discoveredChunks()
                            + "; affected=" + plan.summary().affectedChunks()
                            + "; selected=" + plan.summary().selectedChunks() + "; deferred=" + plan.summary().deferredChunks());
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            getLogger().log(Level.SEVERE, preparationAttempted
                    ? "Offline preparation failed; inspect retained backups before opening targets"
                    : "Invalid targets; no world data changed", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Startup work finished; offline preparation requires an exclusive existing session lock");
        getLogger().info("An empty loaded-world list does not establish exclusive file ownership.");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        getLogger().info("Lifecycle: WorldInitEvent; world=" + event.getWorld().getName());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        getLogger().info("Lifecycle: WorldLoadEvent; world=" + event.getWorld().getName());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        getLogger().info("Lifecycle: ServerLoadEvent; type=" + event.getType());
    }

    @Override
    public void onDisable() {
        getLogger().info("Lifecycle: onDisable");
    }
}
