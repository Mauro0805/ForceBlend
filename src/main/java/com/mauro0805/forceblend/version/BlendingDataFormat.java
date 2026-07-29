package com.mauro0805.forceblend.version;

/**
 * On-disk blending marker schema expected by a Minecraft release family.
 */
public enum BlendingDataFormat {
    /**
     * Minecraft 1.18-1.18.2 requires a true {@code old_noise} byte.
     */
    LEGACY_OLD_NOISE,

    /**
     * Minecraft 1.19+ requires the vertical {@code min_section}/{@code max_section} range.
     */
    SECTION_RANGE
}
