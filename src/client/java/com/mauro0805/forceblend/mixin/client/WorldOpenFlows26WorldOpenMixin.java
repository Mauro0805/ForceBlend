package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Minecraft 26.1-26.2 adapter for Mojang's unobfuscated runtime.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows26WorldOpenMixin {
    @ModifyVariable(method = "openWorld", at = @At("HEAD"), argsOnly = true, name = "levelId")
    private String forceblend$beforeWorldOpen(String levelId) {
        WorldOpenHandler.beforeWorldOpen(levelId);
        return levelId;
    }
}
