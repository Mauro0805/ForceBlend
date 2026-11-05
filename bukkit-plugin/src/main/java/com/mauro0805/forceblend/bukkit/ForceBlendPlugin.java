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
        trace("Lifecycle: onLoad");
    }

    @Override
    public void onEnable() {
        trace("Lifecycle: onEnable; loaded worlds=" + getServer().getWorlds().size());
        if (!getServer().getWorlds().isEmpty()) {
            getLogger().severe("Startup work refused: worlds already loaded. Use a full server restart.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        boolean preparationAttempted = false;
        String stage = "target validation";
        try {
            List<Path> targets = WorldTargets.validate(getServer().getWorldContainer().toPath(),
                    TargetConfiguration.worldNames(getConfig().get("worlds")));
            for (Path target : targets) trace("Validated directory: " + target);
            stage = "configuration";
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
                stage = "offline ownership checks";
                if (!getServer().isPrimaryThread()) throw new IOException("Preparation requires the startup thread");
                for (var plugin : getServer().getPluginManager().getPlugins()) {
                    if (plugin != this) throw new IOException("In-server offline preparation requires ForceBlend as the only plugin; use pre-start preparation when other plugins are installed");
                }
                if (!List.of("Spigot", "CraftBukkit", "Paper", "Purpur").contains(getServer().getName())) {
                    throw new IOException("Unverified server implementation; offline preparation refused");
                }
                preparationAttempted = true;
                OfflinePreparation.run(targets, getServer().getWorldContainer().toPath(),
                        BukkitVersion.minecraftVersion(getServer().getBukkitVersion()), processing, getLogger());
            } else if (enabled) {
                stage = "read-only analysis";
                String version = BukkitVersion.minecraftVersion(getServer().getBukkitVersion());
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
                    : "Startup " + stage + " failed; no world data changed", exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("ForceBlend enabled; startup work finished");
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        trace("Lifecycle: WorldInitEvent; world=" + event.getWorld().getName());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        trace("Lifecycle: WorldLoadEvent; world=" + event.getWorld().getName());
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        trace("Lifecycle: ServerLoadEvent; type=" + event.getType());
    }

    @Override
    public void onDisable() {
        trace("Lifecycle: onDisable");
    }

    private void trace(String message) {
        if (getConfig().getBoolean("detailed-logging", false)) getLogger().info(message);
    }
}
