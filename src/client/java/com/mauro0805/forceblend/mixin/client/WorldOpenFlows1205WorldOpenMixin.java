package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft 1.20.5-1.21.11 adapter.
 */
@Mixin(targets = "net.minecraft.class_7196", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows1205WorldOpenMixin {
    @Unique
    private boolean forceblend$continueWorldOpen = true;

    // This audited intermediary target intentionally does not exist in the active 26.2 IDE classpath.
    //noinspection MixinAnnotationTarget
    @ModifyVariable(method = "method_57784", at = @At("HEAD"), argsOnly = true, ordinal = 0, order = 900)
    private String forceblend$beforeWorldOpen(String levelId) {
        forceblend$continueWorldOpen = WorldOpenHandler.beforeWorldOpen(levelId);
        return levelId;
    }

    //noinspection MixinAnnotationTarget
    @Inject(method = "method_57784", at = @At("HEAD"), cancellable = true, order = 1100)
    private void forceblend$cancelWorldOpen(CallbackInfo callback) {
        boolean continueWorldOpen = forceblend$continueWorldOpen;
        forceblend$continueWorldOpen = true;
        if (!continueWorldOpen) {
            callback.cancel();
        }
    }
}
