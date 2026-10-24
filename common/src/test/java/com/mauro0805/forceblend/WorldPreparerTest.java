package com.mauro0805.forceblend;

import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.ForceBlendConfigStore;
import com.mauro0805.forceblend.config.RunMode;
import com.mauro0805.forceblend.processing.ProcessingReport;
import com.mauro0805.forceblend.version.BlendingDataFormat;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

final class WorldPreparerTest {
    @Test
    void headlessRunBacksUpOriginalDataAndSkipsTheSecondOpening(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        Path region = writeChunk(world, 0);
        byte[] original = Files.readAllBytes(region);

        ProcessingReport report = WorldPreparer.prepare("world", world, game, "26.3").orElseThrow();

        assertEquals(1, report.preparedChunks());
        assertTrue(WorldPreparationState.isComplete(world));
        assertTrue(Files.readString(world.resolve(WorldPreparationState.MARKER_FILE)).contains("\"completed\": true"));
        List<Path> backups = backups(game);
        assertEquals(1, backups.size());
        try (ZipFile zip = new ZipFile(backups.get(0).toFile())) {
            assertArrayEquals(original, zip.getInputStream(zip.getEntry("world/region/r.0.0.mca")).readAllBytes());
            assertNull(zip.getEntry("world/" + WorldPreparationState.MARKER_FILE));
        }
        byte[] prepared = Files.readAllBytes(region);
        assertTrue(WorldPreparer.prepare("world", world, game, "26.3").isEmpty());
        assertArrayEquals(prepared, Files.readAllBytes(region));
        assertEquals(1, backups(game).size());
    }

    @Test
    void legacyMarkerIsHonoredEvenWhenItsOldJsonIsNotStrict(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        Path region = writeChunk(world, 0);
        byte[] original = Files.readAllBytes(region);
        Files.writeString(world.resolve(WorldPreparationState.LEGACY_MARKER_FILE), "{\"preparedChunks\": 1,}");

        assertTrue(WorldPreparer.prepare("world", world, game, "26.3").isEmpty());
        assertArrayEquals(original, Files.readAllBytes(region));
        assertFalse(Files.exists(game.resolve("backups")));
    }

    @Test
    void everyOpeningIgnoresCompletionMarkers(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        writeChunk(world, 0);
        WorldPreparationState.markComplete(world);
        saveConfig(game, new ForceBlendConfig(true, false, false, 0, 1, RunMode.EVERY_OPEN));

        assertEquals(1, WorldPreparer.prepare("world", world, game, "26.3").orElseThrow().preparedChunks());
        assertEquals(0, WorldPreparer.prepare("world", world, game, "26.3").orElseThrow().preparedChunks());
        assertEquals(2, backups(game).size());
    }

    @Test
    void chunkLimitDoesNotMarkCompletionWhileWorkRemains(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        writeChunk(world, 0);
        writeChunk(world, 32);
        saveConfig(game, new ForceBlendConfig(true, false, false, 1, 2, RunMode.ONCE));

        assertEquals(1, WorldPreparer.prepare("world", world, game, "26.3").orElseThrow().preparedChunks());
        assertFalse(WorldPreparationState.isComplete(world));
        assertEquals(1, WorldPreparer.prepare("world", world, game, "26.3").orElseThrow().preparedChunks());
        assertTrue(WorldPreparationState.isComplete(world));
        assertTrue(WorldPreparer.prepare("world", world, game, "26.3").isEmpty());
    }

    @Test
    void backupFailurePreventsRegionWritesAndCompletion(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        Path region = writeChunk(world, 0);
        byte[] original = Files.readAllBytes(region);
        Files.writeString(game.resolve("backups"), "unusable backup destination");

        assertThrows(IOException.class, () -> WorldPreparer.prepare("world", world, game, "26.3"));
        assertArrayEquals(original, Files.readAllBytes(region));
        assertFalse(WorldPreparationState.isComplete(world));
    }

    @Test
    void disabledHeadlessProcessingDoesNotWriteWorldState(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        Path region = writeChunk(world, 0);
        byte[] original = Files.readAllBytes(region);
        saveConfig(game, new ForceBlendConfig(false, false, false, 0, 1));

        assertTrue(WorldPreparer.prepare("world", world, game, "26.3").isEmpty());
        assertArrayEquals(original, Files.readAllBytes(region));
        assertFalse(WorldPreparationState.isComplete(world));
    }

    @Test
    void aNewWorldIsNotMarkedBeforeTerrainExists(@TempDir Path game) throws Exception {
        Path world = game.resolve("not-created-yet");
        assertTrue(WorldPreparer.prepare("new-world", world, game, "26.3").isEmpty());
        assertFalse(Files.exists(world));
        assertFalse(Files.exists(game.resolve("backups")));
    }

    @Test
    void rejectsMalformedTerrainWithoutWritingCompletion(@TempDir Path game) throws Exception {
        Path world = Files.createDirectories(game.resolve("world"));
        Path regions = Files.createDirectories(world.resolve("region"));
        Path malformed = regions.resolve("r.0.0.mca");
        byte[] original = {1, 2, 3};
        Files.write(malformed, original);

        assertThrows(IOException.class, () -> WorldPreparer.prepare("world", world, game, "26.3"));
        assertArrayEquals(original, Files.readAllBytes(malformed));
        assertFalse(WorldPreparationState.isComplete(world));
    }

    @Test
    void completionMarkerMustBeARegularFile(@TempDir Path world) throws IOException {
        Files.createDirectory(world.resolve(WorldPreparationState.MARKER_FILE));
        assertThrows(IOException.class, () -> WorldPreparationState.isComplete(world));
    }

    @Test
    void versionRangeIncludesTheAudited26xFamily() {
        for (String version : List.of("26.1", "26.1.2", "26.2", "26.3")) {
            assertEquals(BlendingDataFormat.SECTION_RANGE, SupportedMinecraftVersions.profileFor(version).format());
        }
        assertEquals(BlendingDataFormat.LEGACY_OLD_NOISE, SupportedMinecraftVersions.profileFor("1.18.2").format());
        assertThrows(IllegalArgumentException.class, () -> SupportedMinecraftVersions.profileFor("26.4"));
        assertThrows(IllegalArgumentException.class, () -> SupportedMinecraftVersions.profileFor("26.3.1"));
    }

    private static void saveConfig(Path game, ForceBlendConfig config) throws IOException {
        new ForceBlendConfigStore(ForceBlendConfigStore.defaultPath(game)).save(config);
    }

    private static List<Path> backups(Path game) throws IOException {
        try (var files = Files.list(game.resolve("backups/forceblend/world"))) {
            return files.filter(path -> path.toString().endsWith(".zip")).sorted().toList();
        }
    }

    private static Path writeChunk(Path world, int chunkX) throws IOException {
        Path regions = Files.createDirectories(world.resolve("region"));
        Files.writeString(world.resolve("level.dat"), "test level metadata", StandardCharsets.UTF_8);
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", 4903);
        chunk.putInt("xPos", chunkX);
        chunk.putInt("zPos", 0);
        chunk.putString("Status", "minecraft:full");
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        CompoundTag bottom = new CompoundTag();
        bottom.putByte("Y", (byte) -4);
        sections.add(bottom);
        CompoundTag top = new CompoundTag();
        top.putByte("Y", (byte) 19);
        sections.add(top);
        chunk.put("sections", sections);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream stream = new DeflaterOutputStream(compressed)) {
            new NBTSerializer(false).toStream(new NamedTag("", chunk), stream);
        }
        byte[] payload = compressed.toByteArray();
        Path region = regions.resolve("r." + Math.floorDiv(chunkX, 32) + ".0.mca");
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(3L * 4096);
            file.seek((long) Math.floorMod(chunkX, 32) * 4);
            file.writeInt(2 << 8 | 1);
            file.seek(8192L);
            file.writeInt(payload.length + 1);
            file.writeByte(2);
            file.write(payload);
        }
        return region;
    }
}
