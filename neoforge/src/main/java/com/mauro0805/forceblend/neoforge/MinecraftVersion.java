package com.mauro0805.forceblend.neoforge;

import net.minecraft.SharedConstants;

/** The running Minecraft version as text, which one JAR has to read across 1.20.6 to 26.x. */
public final class MinecraftVersion {
    private MinecraftVersion() {
    }

    /** {@code WorldVersion.getName()} up to 1.21.x became the record accessor {@code name()} in 26.1. */
    public static String name() {
        Object version = SharedConstants.getCurrentVersion();
        for (String accessor : new String[]{"name", "getName"}) {
            try {
                return (String) version.getClass().getMethod(accessor).invoke(version);
            } catch (NoSuchMethodException next) {
                // Try the other spelling.
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot read the Minecraft version", exception);
            }
        }
        throw new IllegalStateException("Cannot read the Minecraft version from " + version.getClass().getName());
    }
}
