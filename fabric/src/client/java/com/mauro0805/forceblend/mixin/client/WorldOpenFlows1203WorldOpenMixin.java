package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.20.3-1.20.4 world-open adapter. */
@Mixin(targets = "net.minecraft.class_7196", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows1203WorldOpenMixin {
    //noinspection MixinAnnotationTarget
    @Inject(method = "method_54618", at = @At("HEAD"), cancellable = true)
    private void forceblend$beforeWorldOpen(String levelId, Runnable onCancel, CallbackInfo callback) {
        if (WorldOpenHandler.intercept(this, "method_54618", new Object[]{levelId, onCancel}, levelId, onCancel)) {
            callback.cancel();
        }
    }
}
