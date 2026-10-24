package com.mauro0805.forceblend.neoforge.mixin;

import com.mauro0805.forceblend.client.WorldOpenWorkflow;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.WorldOpenFlows", remap = false)
abstract class WorldOpenMixin {
    @Inject(
        method = "openWorld(Ljava/lang/String;Ljava/lang/Runnable;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void forceblend$beforeWorldOpen(String levelId, Runnable onCancel, CallbackInfo callback) {
        Path gameDirectory = Minecraft.getInstance().gameDirectory.toPath().toAbsolutePath().normalize();
        Path saves = gameDirectory.resolve("saves");
        Path world = saves.resolve(levelId).normalize();
        if (!world.startsWith(saves) || world.equals(saves)) {
            throw new IllegalArgumentException("World id resolves outside a world save: " + levelId);
        }
        if (WorldOpenWorkflow.intercept(
            this, "openWorld", new Object[]{levelId, onCancel}, levelId,
            world,
            gameDirectory,
            SharedConstants.getCurrentVersion().name(),
            onCancel
        )) {
            callback.cancel();
        }
    }
}
