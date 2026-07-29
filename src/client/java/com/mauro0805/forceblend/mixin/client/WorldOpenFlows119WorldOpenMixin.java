package com.mauro0805.forceblend.mixin.client;

import com.mauro0805.forceblend.client.WorldOpenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Minecraft 1.19-1.20.2 adapter.
 */
@Mixin(targets = "net.minecraft.class_7196", remap = false)
@SuppressWarnings("unused")
abstract class WorldOpenFlows119WorldOpenMixin {
    // This audited intermediary target intentionally does not exist in the active 26.2 IDE classpath.
    //noinspection MixinAnnotationTarget
    @ModifyVariable(method = "method_41894", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private String forceblend$beforeWorldOpen(String levelId) {
        WorldOpenHandler.beforeWorldOpen(levelId);
        return levelId;
    }
}
