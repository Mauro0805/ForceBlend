package com.mauro0805.forceblend.bukkit;

final class BukkitVersion {
    private BukkitVersion() {}

    static String minecraftVersion(String apiVersion) {
        return apiVersion.split("-", 2)[0].replaceFirst("\\.build\\.\\d+$", "");
    }
}
