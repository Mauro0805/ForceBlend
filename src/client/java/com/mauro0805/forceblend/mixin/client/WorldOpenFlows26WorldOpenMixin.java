package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 26.1-26.2 adapter for Mojang's unobfuscated runtime.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows26WorldOpenMixin {
    @Unique
    private boolean forceblend$continueWorldOpen = true;

    @ModifyVariable(method = "openWorld", at = @At("HEAD"), argsOnly = true, name = "levelId", order = 900)
    private String forceblend$beforeWorldOpen(String levelId) {
        forceblend$continueWorldOpen = WorldOpenHandler.beforeWorldOpen(levelId);
        return levelId;
    }

    @Inject(method = "openWorld", at = @At("HEAD"), cancellable = true, order = 1100)
    private void forceblend$cancelWorldOpen(CallbackInfo callback) {
        boolean continueWorldOpen = forceblend$continueWorldOpen;
        forceblend$continueWorldOpen = true;
        if (!continueWorldOpen) {
            callback.cancel();
        }
    }
}
