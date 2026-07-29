package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Minecraft 1.18-1.18.2 adapter. Target and method use audited Fabric intermediary names.
 */
@Mixin(targets = "net.minecraft.class_310", remap = false)
@SuppressWarnings("unused")
abstract class Minecraft118WorldOpenMixin {
    // This audited intermediary target intentionally does not exist in the active 26.2 IDE classpath.
    //noinspection MixinAnnotationTarget
    @ModifyVariable(method = "method_29606", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String forceblend$beforeWorldOpen(String levelId) {
        WorldOpenHandler.beforeWorldOpen(levelId);
        return levelId;
    }
}
