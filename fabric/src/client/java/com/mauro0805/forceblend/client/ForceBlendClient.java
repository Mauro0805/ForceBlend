package com.mauro0805.forceblend.client;

import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ForceBlendClient implements ClientModInitializer {
    public static final String MOD_ID = "forceblend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        String minecraftVersion = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");
        LOGGER.info(
            "ForceBlend initialized for Minecraft {}. Supported stable releases: {}. "
                + "Processing is restricted to local Overworld chunks.",
            minecraftVersion,
            SupportedMinecraftVersions.SUPPORTED_RANGE
        );
    }
}
