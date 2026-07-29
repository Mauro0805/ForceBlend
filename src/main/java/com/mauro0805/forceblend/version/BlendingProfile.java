package com.mauro0805.forceblend.version;

/**
 * Version-independent values used by Minecraft's 1.18+ blending format.
 *
 * @param supportedVersions        user-facing supported Minecraft range
 * @param minimumDirectDataVersion first chunk format that stores 1.18 sections at the root
 * @param minimumSection           lower clamp used by Minecraft's blending data
 * @param maximumSection           upper clamp used by Minecraft's blending data
 * @param format                   on-disk blending marker schema required by the running Minecraft version
 */
public record BlendingProfile(
    String supportedVersions,
    int minimumDirectDataVersion,
    int minimumSection,
    int maximumSection,
    BlendingDataFormat format
) {
    public BlendingProfile {
        if (supportedVersions == null || supportedVersions.isBlank()) {
            throw new IllegalArgumentException("Supported Minecraft versions must not be blank");
        }
        if (minimumDirectDataVersion < 0) {
            throw new IllegalArgumentException("Minimum DataVersion must not be negative");
        }
        if (minimumSection >= maximumSection) {
            throw new IllegalArgumentException("Minimum section must be lower than maximum section");
        }
        if (format == null) {
            throw new IllegalArgumentException("Blending data format must not be null");
        }
    }
}
