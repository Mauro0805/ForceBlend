package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.20.5-1.21.11 world-open adapter. */
@Mixin(targets = "net.minecraft.class_7196", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows1205WorldOpenMixin {
    //noinspection MixinAnnotationTarget
    @Inject(method = "method_57784", at = @At("HEAD"), cancellable = true)
    private void forceblend$beforeWorldOpen(String levelId, Runnable onCancel, CallbackInfo callback) {
        if (WorldOpenHandler.intercept(this, "method_57784", new Object[]{levelId, onCancel}, levelId, onCancel)) {
            callback.cancel();
        }
    }
}
