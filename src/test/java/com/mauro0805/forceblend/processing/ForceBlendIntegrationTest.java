package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingDataFormat;
import com.mauro0805.forceblend.version.BlendingProfile;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class ForceBlendIntegrationTest {
    private static final int CURRENT_DATA_VERSION = 4903;
    private static final BlendingProfile PROFILE = SupportedMinecraftVersions.profileFor("26.2");
    private static final BlendingProfile MINECRAFT_1_18_PROFILE =
        SupportedMinecraftVersions.profileFor("1.18.2");

    private static CompoundTag fixtureTag(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", CURRENT_DATA_VERSION);
        tag.putInt("xPos", 0);
        tag.putInt("zPos", 0);
        tag.putString("Status", "minecraft:full");
        tag.putString("forceblend_test_marker", marker);
        tag.putByte("isLightOn", (byte) 1);
        tag.put("Heightmaps", new CompoundTag());

        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        CompoundTag bottomSection = new CompoundTag();
        bottomSection.putByte("Y", (byte) -4);
        sections.add(bottomSection);
        CompoundTag topSection = new CompoundTag();
        topSection.putByte("Y", (byte) 19);
        sections.add(topSection);
        tag.put("sections", sections);

        CompoundTag preserved = new CompoundTag();
        preserved.putLong("value", 123456789L);
        tag.put("preserved", preserved);
        return tag;
    }

    private static void assertOriginalFields(CompoundTag tag) {
        assertEquals(0, tag.getInt("xPos"));
        assertEquals(0, tag.getInt("zPos"));
        assertEquals("minecraft:full", tag.getString("Status"));
        assertEquals("overworld", tag.getString("forceblend_test_marker"));
        assertEquals(123456789L, tag.getCompoundTag("preserved").getLong("value"));
    }

    private static void writeFixture(
        Path regionDirectory,
        CompoundTag tag,
        RegionCompression compression
    ) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (OutputStream compressed = compression.compress(payloadBytes)) {
            new NBTSerializer(false).toStream(new NamedTag("", tag), compressed);
        }
        byte[] payload = payloadBytes.toByteArray();
        int sectors = (payload.length + 5 + 4095) / 4096;

        Path regionFile = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "rw")) {
            file.setLength((long) (2 + sectors) * 4096);
            file.seek(0);
            file.writeInt(2 << 8 | sectors);
            file.seek(4096);
            file.writeInt(123456789);
            file.seek(8192);
            file.writeInt(payload.length + 1);
            file.writeByte(compression.id());
            file.write(payload);
        }
    }

    private static CompoundTag readFixture(Path regionDirectory) throws IOException {
        Path regionFile = regionDirectory.resolve("r.0.0.mca");
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            int packed = file.readInt();
            int offset = packed >>> 8;
            file.seek((long) offset * 4096);
            int length = file.readInt();
            RegionCompression compression = RegionCompression.fromStoredByte(file.readUnsignedByte());
            byte[] payload = new byte[length - 1];
            file.readFully(payload);
            try (InputStream decompressed = compression.decompress(new ByteArrayInputStream(payload))) {
                NamedTag namedTag = new NBTDeserializer(false).fromStream(decompressed);
                return (CompoundTag) namedTag.getTag();
            }
        }
    }

    @Test
    void processesOnlyTheOverworldAndIsBinaryIdempotent(@TempDir Path worldDirectory) throws Exception {
        Path dimensionsDirectory = worldDirectory.resolve("dimensions").resolve("minecraft");
        Path overworldDirectory = dimensionsDirectory.resolve("overworld");
        Path overworldRegion = Files.createDirectories(overworldDirectory.resolve("region"));
        Path legacyRootRegion = Files.createDirectories(worldDirectory.resolve("region"));
        Path netherRegion = Files.createDirectories(dimensionsDirectory.resolve("the_nether").resolve("region"));
        Path endRegion = Files.createDirectories(dimensionsDirectory.resolve("the_end").resolve("region"));

        writeFixture(overworldRegion, fixtureTag("overworld"), RegionCompression.ZLIB);
        writeFixture(legacyRootRegion, fixtureTag("legacy-root"), RegionCompression.ZLIB);
        writeFixture(netherRegion, fixtureTag("nether"), RegionCompression.ZLIB);
        writeFixture(endRegion, fixtureTag("end"), RegionCompression.ZLIB);

        ProcessingReport firstRun = WorldProcessor.process("test-world", overworldDirectory, PROFILE);
        assertEquals(1, firstRun.regionFiles());
        assertEquals(1, firstRun.discoveredChunks());
        assertEquals(1, firstRun.preparedChunks());
        assertEquals(0, firstRun.repairedChunks());
        assertEquals(0, firstRun.skippedChunks());
        assertEquals(0, firstRun.alreadyPreparedChunks());

        CompoundTag overworldTag = readFixture(overworldRegion);
        assertTrue(ChunkModifier.hasExpectedBlendingData(overworldTag, PROFILE));
        assertEquals(CURRENT_DATA_VERSION, overworldTag.getInt("DataVersion"));
        assertNull(overworldTag.get("Heightmaps"));
        assertNull(overworldTag.get("isLightOn"));
        assertOriginalFields(overworldTag);

        assertEquals(CURRENT_DATA_VERSION, readFixture(legacyRootRegion).getInt("DataVersion"));
        assertEquals(CURRENT_DATA_VERSION, readFixture(netherRegion).getInt("DataVersion"));
        assertEquals(CURRENT_DATA_VERSION, readFixture(endRegion).getInt("DataVersion"));

        Path overworldFile = overworldRegion.resolve("r.0.0.mca");
        byte[] afterFirstRun = Files.readAllBytes(overworldFile);
        ProcessingReport secondRun = WorldProcessor.process("test-world", overworldDirectory, PROFILE);
        byte[] afterSecondRun = Files.readAllBytes(overworldFile);

        assertEquals(0, secondRun.preparedChunks());
        assertEquals(0, secondRun.repairedChunks());
        assertEquals(0, secondRun.skippedChunks());
        assertEquals(1, secondRun.alreadyPreparedChunks());
        assertArrayEquals(afterFirstRun, afterSecondRun);
    }

    @Test
    void preservesAnAlreadyBlendedChunkWithoutInvalidatingCaches() {
        CompoundTag tag = fixtureTag("already-blended");
        CompoundTag blendingData = new CompoundTag();
        blendingData.putInt(ChunkModifier.MIN_SECTION_KEY, PROFILE.minimumSection());
        blendingData.putInt(ChunkModifier.MAX_SECTION_KEY, PROFILE.maximumSection());
        tag.put(ChunkModifier.BLENDING_DATA_KEY, blendingData);

        assertEquals(ChunkModification.ALREADY_PREPARED, ChunkModifier.apply(tag, PROFILE));
        assertEquals(CURRENT_DATA_VERSION, tag.getInt("DataVersion"));
        assertTrue(tag.containsKey("Heightmaps"));
        assertTrue(tag.containsKey("isLightOn"));
    }

    @Test
    void writesTheLegacyOldNoiseMarkerForMinecraft118() {
        CompoundTag tag = fixtureTag("minecraft-1.18");
        tag.putInt("DataVersion", 2975);

        assertEquals(ChunkModification.PREPARED, ChunkModifier.apply(tag, MINECRAFT_1_18_PROFILE));

        CompoundTag blendingData = tag.getCompoundTag(ChunkModifier.BLENDING_DATA_KEY);
        assertTrue(blendingData.getBoolean("old_noise"));
        assertNull(blendingData.get(ChunkModifier.MIN_SECTION_KEY));
        assertNull(blendingData.get(ChunkModifier.MAX_SECTION_KEY));
        assertTrue(ChunkModifier.hasExpectedBlendingData(tag, MINECRAFT_1_18_PROFILE));
        assertNull(tag.get("Heightmaps"));
        assertNull(tag.get("isLightOn"));
    }

    @Test
    void repairsAnIncompatibleSectionRangeMarkerForMinecraft118() {
        CompoundTag tag = fixtureTag("repair-minecraft-1.18");
        tag.putInt("DataVersion", 2975);
        CompoundTag incompatibleMarker = new CompoundTag();
        incompatibleMarker.putInt(ChunkModifier.MIN_SECTION_KEY, -4);
        incompatibleMarker.putInt(ChunkModifier.MAX_SECTION_KEY, 20);
        tag.put(ChunkModifier.BLENDING_DATA_KEY, incompatibleMarker);

        assertEquals(ChunkModification.PREPARED, ChunkModifier.apply(tag, MINECRAFT_1_18_PROFILE));

        CompoundTag repairedMarker = tag.getCompoundTag(ChunkModifier.BLENDING_DATA_KEY);
        assertTrue(repairedMarker.getBoolean("old_noise"));
        assertNull(repairedMarker.get(ChunkModifier.MIN_SECTION_KEY));
        assertNull(repairedMarker.get(ChunkModifier.MAX_SECTION_KEY));
    }

    @Test
    void selectsTheBlendingSchemaFromTheRunningMinecraftVersion() {
        assertEquals(
            BlendingDataFormat.LEGACY_OLD_NOISE,
            SupportedMinecraftVersions.profileFor("1.18").format()
        );
        assertEquals(
            BlendingDataFormat.LEGACY_OLD_NOISE,
            SupportedMinecraftVersions.profileFor("1.18.2").format()
        );
        assertEquals(
            BlendingDataFormat.SECTION_RANGE,
            SupportedMinecraftVersions.profileFor("1.19").format()
        );
        assertEquals(
            BlendingDataFormat.SECTION_RANGE,
            SupportedMinecraftVersions.profileFor("26.2").format()
        );
        assertEquals(
            BlendingDataFormat.SECTION_RANGE,
            SupportedMinecraftVersions.profileFor("26.1.2").format()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> SupportedMinecraftVersions.profileFor("1.17.1")
        );
    }

    @Test
    void leavesPre118ChunksForMinecraftsNativeDataFixer() {
        CompoundTag tag = fixtureTag("native-migration");
        tag.putInt("DataVersion", PROFILE.minimumDirectDataVersion() - 1);

        assertEquals(ChunkModification.SKIPPED_NATIVE_MIGRATION, ChunkModifier.apply(tag, PROFILE));
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
    }

    @Test
    void removesAnUnsafeLegacyMarkerFromAPreNoiseChunk() {
        CompoundTag tag = fixtureTag("unsafe-legacy-marker");
        tag.putInt("DataVersion", 2975);
        tag.putString("Status", "structure_starts");
        CompoundTag blendingData = new CompoundTag();
        blendingData.putBoolean("old_noise", true);
        tag.put(ChunkModifier.BLENDING_DATA_KEY, blendingData);

        assertEquals(
            ChunkModification.REPAIRED_UNSAFE_MARKER,
            ChunkModifier.apply(tag, MINECRAFT_1_18_PROFILE)
        );
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
        assertTrue(tag.containsKey("Heightmaps"));
        assertTrue(tag.containsKey("isLightOn"));
    }

    @Test
    void repairsAnUnsafeMarkerTransactionallyAndBecomesIdempotent(@TempDir Path worldDirectory)
        throws Exception {
        Path overworldDirectory = worldDirectory.resolve("overworld");
        Path regionDirectory = Files.createDirectories(overworldDirectory.resolve("region"));
        CompoundTag tag = fixtureTag("transactional-repair");
        tag.putInt("DataVersion", 2975);
        tag.putString("Status", "structure_starts");
        CompoundTag blendingData = new CompoundTag();
        blendingData.putBoolean("old_noise", true);
        tag.put(ChunkModifier.BLENDING_DATA_KEY, blendingData);
        writeFixture(regionDirectory, tag, RegionCompression.ZLIB);

        ProcessingReport firstReport = WorldProcessor.process(
            "test-world",
            overworldDirectory,
            MINECRAFT_1_18_PROFILE
        );
        assertEquals(0, firstReport.preparedChunks());
        assertEquals(1, firstReport.repairedChunks());
        assertEquals(0, firstReport.skippedChunks());
        assertEquals(0, firstReport.alreadyPreparedChunks());
        CompoundTag repaired = readFixture(regionDirectory);
        assertNull(repaired.get(ChunkModifier.BLENDING_DATA_KEY));
        assertTrue(repaired.containsKey("Heightmaps"));
        assertTrue(repaired.containsKey("isLightOn"));

        Path regionFile = regionDirectory.resolve("r.0.0.mca");
        byte[] afterRepair = Files.readAllBytes(regionFile);
        ProcessingReport secondReport = WorldProcessor.process(
            "test-world",
            overworldDirectory,
            MINECRAFT_1_18_PROFILE
        );
        assertEquals(0, secondReport.preparedChunks());
        assertEquals(0, secondReport.repairedChunks());
        assertEquals(1, secondReport.skippedChunks());
        assertEquals(0, secondReport.alreadyPreparedChunks());
        assertArrayEquals(afterRepair, Files.readAllBytes(regionFile));
    }

    @Test
    void removesAnUnsafeModernMarkerFromAPreNoiseChunk() {
        CompoundTag tag = fixtureTag("unsafe-modern-marker");
        tag.putString("Status", "minecraft:biomes");
        CompoundTag blendingData = new CompoundTag();
        blendingData.putInt(ChunkModifier.MIN_SECTION_KEY, -4);
        blendingData.putInt(ChunkModifier.MAX_SECTION_KEY, 20);
        tag.put(ChunkModifier.BLENDING_DATA_KEY, blendingData);

        assertEquals(ChunkModification.REPAIRED_UNSAFE_MARKER, ChunkModifier.apply(tag, PROFILE));
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
    }

    @Test
    void skipsAnUnmarkedPreNoiseChunk() {
        CompoundTag tag = fixtureTag("pre-noise");
        tag.putString("Status", "minecraft:structure_references");

        assertEquals(ChunkModification.SKIPPED_INCOMPLETE, ChunkModifier.apply(tag, PROFILE));
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
        assertTrue(tag.containsKey("Heightmaps"));
        assertTrue(tag.containsKey("isLightOn"));
    }

    @Test
    void skipsANoiseChunkWithAnUnnamespacedStatus() {
        CompoundTag tag = fixtureTag("noise");
        tag.putInt("DataVersion", 2975);
        tag.putString("Status", "noise");

        assertEquals(ChunkModification.SKIPPED_INCOMPLETE, ChunkModifier.apply(tag, MINECRAFT_1_18_PROFILE));
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
        assertTrue(tag.containsKey("Heightmaps"));
        assertTrue(tag.containsKey("isLightOn"));
    }

    @Test
    void removesAnOlderForceBlendMarkerFromAPartialNoiseChunk() {
        CompoundTag tag = fixtureTag("partial-noise-marker");
        tag.putInt("DataVersion", 2975);
        tag.putString("Status", "minecraft:noise");
        CompoundTag blendingData = new CompoundTag();
        blendingData.putBoolean("old_noise", true);
        tag.put(ChunkModifier.BLENDING_DATA_KEY, blendingData);

        assertEquals(
            ChunkModification.REPAIRED_UNSAFE_MARKER,
            ChunkModifier.apply(tag, MINECRAFT_1_18_PROFILE)
        );
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
        assertTrue(tag.containsKey("Heightmaps"));
        assertTrue(tag.containsKey("isLightOn"));
    }

    @Test
    void preparesAFullChunkWithAnUnnamespacedStatus() {
        CompoundTag tag = fixtureTag("unnamespaced-full");
        tag.putString("Status", "full");

        assertEquals(ChunkModification.PREPARED, ChunkModifier.apply(tag, PROFILE));
        assertTrue(ChunkModifier.hasExpectedBlendingData(tag, PROFILE));
    }

    @Test
    void skipsAChunkWithoutAStatus() {
        CompoundTag tag = fixtureTag("missing-status");
        tag.remove("Status");

        assertEquals(ChunkModification.SKIPPED_INCOMPLETE, ChunkModifier.apply(tag, PROFILE));
        assertNull(tag.get(ChunkModifier.BLENDING_DATA_KEY));
    }

    @Test
    void rejectsChunksWithoutADataVersion() {
        assertThrows(IllegalArgumentException.class, () -> ChunkModifier.apply(new CompoundTag(), PROFILE));
    }

    @Test
    void rejectsANonIntegerDataVersion() {
        CompoundTag tag = fixtureTag("wrong-data-version-type");
        tag.putString("DataVersion", Integer.toString(CURRENT_DATA_VERSION));

        assertThrows(IllegalArgumentException.class, () -> ChunkModifier.apply(tag, PROFILE));
    }

    @Test
    void rejectsAChunkStoredAtTheWrongRegionPositionWithoutChangingTheRegion(@TempDir Path worldDirectory)
        throws Exception {
        Path overworldDirectory = worldDirectory.resolve("dimensions").resolve("minecraft").resolve("overworld");
        Path regionDirectory = Files.createDirectories(overworldDirectory.resolve("region"));
        CompoundTag tag = fixtureTag("wrong-position");
        tag.putInt("xPos", 1);
        writeFixture(regionDirectory, tag, RegionCompression.ZLIB);
        Path regionFile = regionDirectory.resolve("r.0.0.mca");
        byte[] before = Files.readAllBytes(regionFile);

        assertThrows(
            IOException.class,
            () -> WorldProcessor.process("test-world", overworldDirectory, PROFILE)
        );
        assertArrayEquals(before, Files.readAllBytes(regionFile));
    }

    @Test
    void readsAndWritesModernLz4ChunkStreams(@TempDir Path worldDirectory) throws Exception {
        Path overworldDirectory = worldDirectory.resolve("overworld");
        Path regionDirectory = Files.createDirectories(overworldDirectory.resolve("region"));
        writeFixture(regionDirectory, fixtureTag("lz4"), RegionCompression.LZ4);

        ProcessingReport report = WorldProcessor.process("test-world", overworldDirectory, PROFILE);

        assertEquals(1, report.preparedChunks());
        assertTrue(ChunkModifier.hasExpectedBlendingData(readFixture(regionDirectory), PROFILE));
    }

    @Test
    void rejectsRegionCoordinatesThatOverflowTheChunkRange(@TempDir Path worldDirectory) throws Exception {
        Path overworldDirectory = worldDirectory.resolve("dimensions").resolve("minecraft").resolve("overworld");
        Path regionDirectory = Files.createDirectories(overworldDirectory.resolve("region"));
        Files.createFile(regionDirectory.resolve("r.2147483647.0.mca"));

        assertThrows(
            IOException.class,
            () -> WorldProcessor.process("test-world", overworldDirectory, PROFILE)
        );
    }
}
