package com.mauro0805.forceblend.version;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central registry for stable Minecraft release families and their storage profiles.
 */
public final class SupportedMinecraftVersions {
    public static final String SUPPORTED_RANGE = "1.18-1.21.11 and 26.1-26.3";

    private static final int MINIMUM_DIRECT_DATA_VERSION = 2844;
    private static final int MINIMUM_SECTION = -4;
    private static final int MAXIMUM_SECTION = 20;
    private static final Pattern STABLE_VERSION = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$");

    private static final BlendingProfile MINECRAFT_1_18_PROFILE = profile(
        "1.18-1.18.2",
        BlendingDataFormat.LEGACY_OLD_NOISE
    );
    private static final BlendingProfile SECTION_RANGE_PROFILE = profile(
        "1.19-1.21.11 and 26.1-26.3",
        BlendingDataFormat.SECTION_RANGE
    );

    private SupportedMinecraftVersions() {
    }

    public static ReleaseFamily familyFor(String version) {
        if (version == null) {
            throw new IllegalArgumentException("Minecraft version must not be null");
        }

        Matcher matcher = STABLE_VERSION.matcher(version);
        if (!matcher.matches()) {
            throw unsupported(version);
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));

        if (major == 1 && minor == 18 && patch <= 2) {
            return ReleaseFamily.MINECRAFT_1_18;
        }
        if (major == 1 && (minor == 19 && patch <= 4 || minor == 20 && patch <= 2)) {
            return ReleaseFamily.MINECRAFT_1_19_TO_1_20_2;
        }
        if (major == 1 && minor == 20 && patch <= 4) {
            return ReleaseFamily.MINECRAFT_1_20_3_TO_1_20_4;
        }
        if (major == 1 && (minor == 20 && patch <= 6 || minor == 21 && patch <= 11)) {
            return ReleaseFamily.MINECRAFT_1_20_5_TO_1_21_11;
        }
        if (major == 26 && (minor == 1 || (minor == 2 || minor == 3) && patch == 0)) {
            return ReleaseFamily.MINECRAFT_26_1_TO_26_3;
        }
        throw unsupported(version);
    }

    public static BlendingProfile profileFor(String version) {
        return familyFor(version).profile();
    }

    private static BlendingProfile profile(String versions, BlendingDataFormat format) {
        return new BlendingProfile(
            versions,
            MINIMUM_DIRECT_DATA_VERSION,
            MINIMUM_SECTION,
            MAXIMUM_SECTION,
            format
        );
    }

    private static IllegalArgumentException unsupported(String version) {
        return new IllegalArgumentException("ForceBlend does not support Minecraft " + version);
    }

    public enum ReleaseFamily {
        MINECRAFT_1_18(MINECRAFT_1_18_PROFILE),
        MINECRAFT_1_19_TO_1_20_2(SECTION_RANGE_PROFILE),
        MINECRAFT_1_20_3_TO_1_20_4(SECTION_RANGE_PROFILE),
        MINECRAFT_1_20_5_TO_1_21_11(SECTION_RANGE_PROFILE),
        MINECRAFT_26_1_TO_26_3(SECTION_RANGE_PROFILE);

        private final BlendingProfile profile;

        ReleaseFamily(BlendingProfile profile) {
            this.profile = profile;
        }

        public BlendingProfile profile() {
            return profile;
        }
    }
}
