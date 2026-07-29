package com.mauro0805.forceblend.processing;

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

final class ContinuousFrontierProcessingTest {
    private static final BlendingProfile PROFILE = SupportedMinecraftVersions.profileFor("26.2");

    private static CompoundTag fixtureTag(int chunkX, int chunkZ) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", 4903);
        tag.putInt("xPos", chunkX);
        tag.putInt("zPos", chunkZ);
        tag.putString("Status", "minecraft:full");
        tag.putByte("isLightOn", (byte) 1);
        tag.put("Heightmaps", new CompoundTag());

        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        CompoundTag bottom = new CompoundTag();
        bottom.putByte("Y", (byte) -4);
        sections.add(bottom);
        CompoundTag top = new CompoundTag();
        top.putByte("Y", (byte) 19);
        sections.add(top);
        tag.put("sections", sections);
        return tag;
    }

    private static void writeRegion(Path regionDirectory, int chunkX, int chunkZ, CompoundTag tag)
        throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        try (OutputStream compressed = RegionCompression.ZLIB.compress(payloadBytes)) {
            new NBTSerializer(false).toStream(new NamedTag("", tag), compressed);
        }
        byte[] payload = payloadBytes.toByteArray();
        int sectors = (payload.length + 5 + 4095) / 4096;
        int localIndex = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        Path regionFile = regionDirectory.resolve(
            "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"
        );

        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "rw")) {
            file.setLength((long) (2 + sectors) * 4096);
            file.seek((long) localIndex * Integer.BYTES);
            file.writeInt(2 << 8 | sectors);
            file.seek(4096L + (long) localIndex * Integer.BYTES);
            file.writeInt(123456789);
            file.seek(8192);
            file.writeInt(payload.length + 1);
            file.writeByte(RegionCompression.ZLIB.id());
            file.write(payload);
        }
    }

    private static CompoundTag readRegion(Path regionDirectory, int chunkX, int chunkZ) throws IOException {
        int localIndex = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        Path regionFile = regionDirectory.resolve(
            "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"
        );
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            file.seek((long) localIndex * Integer.BYTES);
            int packed = file.readInt();
            file.seek((long) (packed >>> 8) * 4096);
            int length = file.readInt();
            RegionCompression compression = RegionCompression.fromStoredByte(file.readUnsignedByte());
            byte[] payload = new byte[length - 1];
            file.readFully(payload);
            try (InputStream decompressed = compression.decompress(new ByteArrayInputStream(payload))) {
                return (CompoundTag) new NBTDeserializer(false).fromStream(decompressed).getTag();
            }
        }
    }

    @Test
    void detectsAChunkImportedAfterAnEarlierSuccessfulOpen(@TempDir Path worldDirectory) throws Exception {
        Path regionDirectory = Files.createDirectories(worldDirectory.resolve("region"));
        writeRegion(regionDirectory, 0, 0, fixtureTag(0, 0));

        ProcessingReport first = WorldProcessor.process("continuous-test", worldDirectory, PROFILE);
        assertEquals(1, first.preparedChunks());

        writeRegion(regionDirectory, 32, 5, fixtureTag(32, 5));
        ProcessingReport second = WorldProcessor.process("continuous-test", worldDirectory, PROFILE);

        assertEquals(2, second.frontierChunks());
        assertEquals(1, second.preparedChunks());
        assertEquals(1, second.alreadyPreparedChunks());
        assertTrue(ChunkModifier.hasExpectedBlendingData(readRegion(regionDirectory, 32, 5), PROFILE));
        assertFalse(Files.exists(worldDirectory.resolve("forceblend-state.properties")));
    }

    @Test
    void repairsAnUnsafePartialMarkerEvenWithoutACompleteTerrainFrontier(@TempDir Path worldDirectory)
        throws Exception {
        Path regionDirectory = Files.createDirectories(worldDirectory.resolve("region"));
        CompoundTag partial = fixtureTag(0, 0);
        partial.putString("Status", "minecraft:structure_starts");
        CompoundTag unsafeMarker = new CompoundTag();
        unsafeMarker.putInt(ChunkModifier.MIN_SECTION_KEY, -4);
        unsafeMarker.putInt(ChunkModifier.MAX_SECTION_KEY, 20);
        partial.put(ChunkModifier.BLENDING_DATA_KEY, unsafeMarker);
        writeRegion(regionDirectory, 0, 0, partial);

        ProcessingReport report = WorldProcessor.process("repair-test", worldDirectory, PROFILE);

        assertEquals(0, report.frontierChunks());
        assertEquals(1, report.repairedChunks());
        assertNull(readRegion(regionDirectory, 0, 0).get(ChunkModifier.BLENDING_DATA_KEY));
    }
}
