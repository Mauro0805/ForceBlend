package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import com.mauro0805.forceblend.client.ui.NativeScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.19-1.20.2 world-open adapter. */
@Mixin(targets = "net.minecraft.class_7196", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows119WorldOpenMixin {
    //noinspection MixinAnnotationTarget
    @Inject(method = "method_41894", at = @At("HEAD"), cancellable = true)
    private void forceblend$beforeWorldOpen(@Coerce Object parent, String levelId, CallbackInfo callback) {
        if (WorldOpenHandler.intercept(this, "method_41894", new Object[]{parent, levelId}, levelId, () -> NativeScreen.show(parent))) {
            callback.cancel();
        }
    }
}
