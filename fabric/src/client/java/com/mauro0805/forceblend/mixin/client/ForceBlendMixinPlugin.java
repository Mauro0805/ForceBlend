package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Selects exactly one lifecycle adapter before Mixin resolves any Minecraft target classes.
 */
public final class ForceBlendMixinPlugin implements IMixinConfigPlugin {
    private String selectedMixin;

    static String selectMixin(SupportedMinecraftVersions.ReleaseFamily family) {
        return switch (family) {
            case MINECRAFT_1_18 -> "Minecraft118WorldOpenMixin";
            case MINECRAFT_1_19_TO_1_20_2 -> "WorldOpenFlows119WorldOpenMixin";
            case MINECRAFT_1_20_3_TO_1_20_4 -> "WorldOpenFlows1203WorldOpenMixin";
            case MINECRAFT_1_20_5_TO_1_21_11 -> "WorldOpenFlows1205WorldOpenMixin";
            case MINECRAFT_26_1_TO_26_3 -> "WorldOpenFlows26WorldOpenMixin";
        };
    }

    @Override
    public void onLoad(String mixinPackage) {
        String version = FabricLoader.getInstance()
            .getModContainer("minecraft")
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElseThrow(() -> new IllegalStateException("Minecraft version metadata is unavailable"));
        try {
            selectedMixin = selectMixin(SupportedMinecraftVersions.familyFor(version));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return mixinClassName.endsWith('.' + selectedMixin);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // No cross-config target negotiation is required.
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
        // No bytecode inspection is required.
    }

    @Override
    public void postApply(
        String targetClassName,
        ClassNode targetClass,
        String mixinClassName,
        IMixinInfo mixinInfo
    ) {
        // No bytecode post-processing is required.
    }
}
