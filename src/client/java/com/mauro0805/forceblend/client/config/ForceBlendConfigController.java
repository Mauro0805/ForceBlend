package com.mauro0805.forceblend.client.config;

import com.mauro0805.forceblend.client.ForceBlendClient;
import com.mauro0805.forceblend.client.ui.ForceBlendDesktopUi;
import com.mauro0805.forceblend.config.ConfigLoadResult;
import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.ForceBlendConfigStore;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public final class ForceBlendConfigController {
    private ForceBlendConfigController() {
    }

    public static ForceBlendConfig load() {
        ForceBlendConfigStore store = store();
        try {
            ConfigLoadResult result = store.loadOrCreate();
            result.warnings().forEach(warning -> ForceBlendClient.LOGGER.warn("ForceBlend config: {}", warning));
            return result.config();
        } catch (IOException | RuntimeException exception) {
            ForceBlendClient.LOGGER.error(
                "Could not load {}; using ForceBlend defaults for this session",
                store.configFile(),
                exception
            );
            return ForceBlendConfig.DEFAULTS;
        }
    }

    public static void openEditor() {
        ForceBlendConfigStore store = store();
        ForceBlendConfig current;
        try {
            ConfigLoadResult result = store.loadOrCreate();
            result.warnings().forEach(warning -> ForceBlendClient.LOGGER.warn("ForceBlend config: {}", warning));
            current = result.config();
        } catch (IOException | RuntimeException exception) {
            ForceBlendClient.LOGGER.error("Could not load {}; opening defaults", store.configFile(), exception);
            current = ForceBlendConfig.DEFAULTS;
        }

        Optional<ForceBlendConfig> edited = ForceBlendDesktopUi.editConfiguration(current);
        if (edited.isEmpty()) {
            return;
        }
        try {
            store.save(edited.orElseThrow());
            ForceBlendClient.LOGGER.info("Saved ForceBlend settings to {}", store.configFile());
        } catch (IOException | RuntimeException exception) {
            ForceBlendClient.LOGGER.error("Could not save ForceBlend settings to {}", store.configFile(), exception);
            ForceBlendDesktopUi.showSettingsFailure(exception);
        }
    }

    private static ForceBlendConfigStore store() {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        return new ForceBlendConfigStore(ForceBlendConfigStore.defaultPath(gameDirectory));
    }
}
