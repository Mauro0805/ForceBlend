package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.18-1.18.2 world-open adapter. */
@Mixin(targets = "net.minecraft.class_310", remap = false)
@SuppressWarnings("unused")
abstract class Minecraft118WorldOpenMixin {
    //noinspection MixinAnnotationTarget
    @Inject(method = "method_29606", at = @At("HEAD"), cancellable = true)
    private void forceblend$beforeWorldOpen(String levelId, CallbackInfo callback) {
        if (WorldOpenHandler.intercept(this, "method_29606", new Object[]{levelId}, levelId, WorldOpenHandler.returnToCurrentScreen())) {
            callback.cancel();
        }
    }
}
