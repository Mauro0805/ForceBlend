package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingDataFormat;
import com.mauro0805.forceblend.version.BlendingProfile;
import net.querz.nbt.tag.*;

import java.util.Objects;

/**
 * The only component allowed to mutate chunk NBT.
 */
public final class ChunkModifier {
    public static final String DATA_VERSION_KEY = "DataVersion";
    public static final String BLENDING_DATA_KEY = "blending_data";
    public static final String MIN_SECTION_KEY = "min_section";
    public static final String MAX_SECTION_KEY = "max_section";

    private ChunkModifier() {
    }

    /**
     * Applies Minecraft's marker to eligible chunks and removes it from incomplete chunks.
     * Chunks older than the 1.18 format are left for Minecraft's native data fixer.
     *
     * @return the precise outcome used for transactional writes and diagnostics
     */
    public static ChunkModification apply(CompoundTag chunkTag, BlendingProfile profile) {
        Objects.requireNonNull(chunkTag, "chunkTag");
        Objects.requireNonNull(profile, "profile");

        int dataVersion = requireDataVersion(chunkTag);
        if (dataVersion < profile.minimumDirectDataVersion()) {
            return ChunkModification.SKIPPED_NATIVE_MIGRATION;
        }

        if (!ChunkStatusPolicy.isEligibleForBlending(chunkTag)) {
            return chunkTag.remove(BLENDING_DATA_KEY) == null
                ? ChunkModification.SKIPPED_INCOMPLETE
                : ChunkModification.REPAIRED_UNSAFE_MARKER;
        }

        SectionRange range = findSectionRange(chunkTag, profile);
        if (range == null) {
            return ChunkModification.SKIPPED_INCOMPLETE;
        }
        if (hasExpectedBlendingData(chunkTag, profile, range)) {
            return ChunkModification.ALREADY_PREPARED;
        }
        CompoundTag blendingData = new CompoundTag();
        if (profile.format() == BlendingDataFormat.LEGACY_OLD_NOISE) {
            blendingData.putBoolean("old_noise", true);
        } else {
            blendingData.putInt(MIN_SECTION_KEY, range.minimum());
            blendingData.putInt(MAX_SECTION_KEY, range.maximum());
        }
        chunkTag.put(BLENDING_DATA_KEY, blendingData);

        // These values are derived from terrain and must be recalculated at the new border.
        chunkTag.remove("Heightmaps");
        chunkTag.remove("isLightOn");
        return ChunkModification.PREPARED;
    }

    static ChunkInspection inspect(CompoundTag chunkTag, BlendingProfile profile) {
        Objects.requireNonNull(chunkTag, "chunkTag");
        Objects.requireNonNull(profile, "profile");

        int dataVersion = requireDataVersion(chunkTag);
        boolean completeTerrain = ChunkStatusPolicy.isEligibleForBlending(chunkTag);
        boolean unsafeMarker = dataVersion >= profile.minimumDirectDataVersion()
            && !completeTerrain
            && chunkTag.containsKey(BLENDING_DATA_KEY);
        return new ChunkInspection(completeTerrain, unsafeMarker);
    }

    public static boolean hasExpectedBlendingData(CompoundTag chunkTag, BlendingProfile profile) {
        Objects.requireNonNull(chunkTag, "chunkTag");
        Objects.requireNonNull(profile, "profile");

        if (!ChunkStatusPolicy.isEligibleForBlending(chunkTag)) {
            return false;
        }
        SectionRange range = findSectionRange(chunkTag, profile);
        return range != null && hasExpectedBlendingData(chunkTag, profile, range);
    }

    private static boolean hasExpectedBlendingData(
        CompoundTag chunkTag,
        BlendingProfile profile,
        SectionRange range
    ) {
        CompoundTag blendingData = chunkTag.getCompoundTag(BLENDING_DATA_KEY);
        if (blendingData == null) {
            return false;
        }
        if (profile.format() == BlendingDataFormat.LEGACY_OLD_NOISE) {
            Tag<?> rawOldNoise = blendingData.get("old_noise");
            return rawOldNoise instanceof ByteTag oldNoise && oldNoise.asByte() != 0;
        }
        Tag<?> rawMinimum = blendingData.get(MIN_SECTION_KEY);
        Tag<?> rawMaximum = blendingData.get(MAX_SECTION_KEY);
        return rawMinimum instanceof IntTag minimum
            && rawMaximum instanceof IntTag maximum
            && minimum.asInt() == range.minimum()
            && maximum.asInt() == range.maximum();
    }

    private static SectionRange findSectionRange(CompoundTag chunkTag, BlendingProfile profile) {
        ListTag<?> sections = chunkTag.getListTag("sections");
        if (sections == null) {
            return null;
        }

        int minimum = 0;
        int maximum = 0;
        for (CompoundTag section : sections.asCompoundTagList()) {
            Tag<?> rawY = section.get("Y");
            if (rawY instanceof NumberTag<?> y) {
                minimum = Math.min(minimum, y.asInt());
                maximum = Math.max(maximum, y.asInt());
            }
        }
        return new SectionRange(
            Math.min(minimum, profile.minimumSection()),
            Math.max(maximum, profile.maximumSection())
        );
    }

    private static int requireDataVersion(CompoundTag chunkTag) {
        Tag<?> rawDataVersion = chunkTag.get(DATA_VERSION_KEY);
        if (!(rawDataVersion instanceof IntTag dataVersionTag) || dataVersionTag.asInt() < 0) {
            throw new IllegalArgumentException("Chunk is missing a valid integer DataVersion");
        }
        return dataVersionTag.asInt();
    }

    record ChunkInspection(boolean completeTerrain, boolean unsafeMarker) {
    }

    private record SectionRange(int minimum, int maximum) {
    }
}
