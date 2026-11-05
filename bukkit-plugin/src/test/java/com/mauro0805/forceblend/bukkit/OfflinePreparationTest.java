package com.mauro0805.forceblend.bukkit;

import com.mauro0805.forceblend.config.ForceBlendConfig;
import com.mauro0805.forceblend.config.RunMode;
import com.mauro0805.forceblend.WorldPreparationState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

class OfflinePreparationTest {
    @TempDir Path game;
    private final Logger logger = Logger.getLogger("offline-test");

    @Test
    void acceptsOnlyTheVerifiedStableUnobfuscatedMetadataAlias() {
        CompoundTag version = new CompoundTag();
        version.putString("Name", "1.21.11 Unobfuscated");
        version.putInt("Id", 4671);
        version.putString("Series", "main");
        version.putByte("Snapshot", (byte) 0);
        assertTrue(OfflinePreparation.matchesVersion("1.21.11", version, 4671));
        assertFalse(OfflinePreparation.matchesVersion("1.21.10", version, 4671));
        assertFalse(OfflinePreparation.matchesVersion("1.21.11", version, 4670));
        version.putInt("Id", 4670);
        assertFalse(OfflinePreparation.matchesVersion("1.21.11", version, 4671));
        version.putInt("Id", 4671);
        version.putByte("Snapshot", (byte) 1);
        assertFalse(OfflinePreparation.matchesVersion("1.21.11", version, 4671));
        version.remove("Snapshot");
        assertFalse(OfflinePreparation.matchesVersion("1.21.11", version, 4671));
        version.putByte("Snapshot", (byte) 0);
        version.putString("Series", "other");
        assertFalse(OfflinePreparation.matchesVersion("1.21.11", version, 4671));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.17", "1.22", "26.4", "26.2-snapshot"})
    void completionNeverBypassesUnsupportedVersionRefusal(String version) throws IOException {
        Path world = world(version);
        WorldPreparationState.markComplete(world);
        assertThrows(IOException.class, () -> OfflinePreparation.run(List.of(world), game, version, config(RunMode.ONCE), logger));
        assertFalse(Files.exists(game.resolve("backups")));
    }

    @Test
    void preStartPreparesPrimaryWorldAndSkipsCompletedReopen() throws IOException {
        Path world = world("1.18.2");
        Files.writeString(game.resolve("forceblend-prestart.properties"), "accept-world-preparation=true\n");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            String[] args = {"--world-container", game.toString(), "--world", "world", "--minecraft-version", "1.18.2", "--action", "prepare"};
            assertEquals(0, ForceBlendPreStart.execute(args, output, output), bytes.toString(StandardCharsets.UTF_8));
            assertTrue(Files.isRegularFile(world.resolve("forceblend.json")));
            assertEquals(0, ForceBlendPreStart.execute(args, output, output));
            assertEquals(1, backups().size());
            assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("Once mode skipped"));
        }
    }

    @Test
    void preStartRequiresSettingsAcceptanceBeforeAnyWorkerOrBackup() throws IOException {
        Path world = world("1.18.2");
        byte[] before = Files.readAllBytes(world.resolve("region/r.0.0.mca"));
        String[] args = {"--world-container", game.toString(), "--world", "world", "--minecraft-version", "1.18.2"};
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            assertNotEquals(0, ForceBlendPreStart.execute(args, output, output));
            Files.writeString(game.resolve("forceblend-prestart.properties"), "accept-world-preparation=false\n");
            assertNotEquals(0, ForceBlendPreStart.execute(args, output, output));
        }
        assertArrayEquals(before, Files.readAllBytes(world.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(game.resolve("backups")));
    }

    @Test
    void settingsControlAutomaticExecutionAndEveryOpenMode() throws IOException {
        Path world = world("1.18.2");
        Path settings = game.resolve("forceblend-prestart.properties");
        String[] args = {"--world-container", game.toString(), "--world", "world", "--minecraft-version", "1.18.2"};
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            Files.writeString(settings, "accept-world-preparation=true\nautomatic-processing=false\n");
            assertEquals(0, ForceBlendPreStart.execute(args, output, output));
            assertFalse(Files.exists(game.resolve("backups")));
            Files.writeString(settings, "accept-world-preparation=true\nrun-mode=every-open\nprocessing-threads=1\nmaximum-chunks-per-run=1\n");
            assertEquals(0, ForceBlendPreStart.execute(args, output, output));
            assertFalse(Files.exists(world.resolve("forceblend.json")));
            assertEquals(1, backups().size());
            assertEquals(0, ForceBlendPreStart.execute(args, output, output));
            assertEquals(2, backups().size());
        }
    }

    @Test
    void preStartAnalysisDoesNotChangeWorldOrCreateBackup() throws IOException {
        Path world = world("1.18.2");
        byte[] original = Files.readAllBytes(world.resolve("region/r.0.0.mca"));
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            assertEquals(0, ForceBlendPreStart.execute(new String[]{"--world-container", game.toString(),
                    "--world", "world", "--minecraft-version", "1.18.2", "--action", "analyze"}, output, output));
        }
        assertArrayEquals(original, Files.readAllBytes(world.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(game.resolve("backups")));
        assertFalse(Files.exists(world.resolve("forceblend.json")));
    }

    @Test
    void preStartRejectsIncompleteUnknownAndInvalidOptions() {
        try (PrintStream output = new PrintStream(new ByteArrayOutputStream())) {
            assertEquals(0, ForceBlendPreStart.execute(new String[]{"--help"}, output, output));
            for (String[] args : List.of(new String[0], new String[]{"--world"},
                    new String[]{"--unknown", "value"}, new String[]{"--processing-threads", "1.5"},
                    new String[]{"--detailed-logging", "yes"}, new String[]{"--run-mode", "once", "--run-mode", "every-open"})) {
                assertNotEquals(0, ForceBlendPreStart.execute(args, output, output));
            }
        }
    }

    @Test
    void preInterruptedRunDoesNotStartOrCreateBackup() throws IOException {
        Path world = world("1.18.2");
        Thread.currentThread().interrupt();
        try {
            assertThrows(IOException.class, () -> OfflinePreparation.run(List.of(world), game, "1.18.2", config(RunMode.ONCE), logger));
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(game.resolve("backups")));
        } finally { Thread.interrupted(); }
    }

    @Test
    void backsUpThenAppliesAndOnceModeDoesNotRepeat() throws IOException {
        Path world = world("1.18.2");
        byte[] before = Files.readAllBytes(world.resolve("region/r.0.0.mca"));
        OfflinePreparation.run(List.of(world), game, "1.18.2", config(RunMode.ONCE), logger);
        assertTrue(Files.isRegularFile(world.resolve("forceblend.json")));
        assertFalse(Arrays.equals(before, Files.readAllBytes(world.resolve("region/r.0.0.mca"))));
        List<Path> backups = backups();
        assertEquals(1, backups.size());
        try (ZipFile zip = new ZipFile(backups.get(0).toFile())) {
            assertArrayEquals(before, zip.getInputStream(zip.getEntry("world/region/r.0.0.mca")).readAllBytes());
            assertNull(zip.getEntry("world/session.lock"));
        }
        OfflinePreparation.run(List.of(world), game, "1.18.2", config(RunMode.ONCE), logger);
        assertEquals(1, backups().size());
    }

    @Test
    void backupFailureAndVersionMismatchDoNotWriteTerrain() throws IOException {
        Path world = world("1.18.2");
        byte[] before = Files.readAllBytes(world.resolve("region/r.0.0.mca"));
        assertThrows(IOException.class, () -> OfflinePreparation.run(List.of(world), game, "1.19.4", config(RunMode.ONCE), logger));
        Files.writeString(game.resolve("backups"), "blocked");
        assertThrows(IOException.class, () -> OfflinePreparation.run(List.of(world), game, "1.18.2", config(RunMode.ONCE), logger));
        assertArrayEquals(before, Files.readAllBytes(world.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(world.resolve("forceblend.json")));
        try (WorldLeases next = WorldLeases.acquire(List.of(world))) { next.validate(); }
    }

    private ForceBlendConfig config(RunMode mode) { return new ForceBlendConfig(true, true, false, 0, 1, mode); }
    private List<Path> backups() throws IOException {
        try (var files = Files.list(game.resolve("backups/forceblend/world"))) { return files.toList(); }
    }
    private Path world(String version) throws IOException {
        Path world = Files.createDirectory(game.resolve("world"));
        Files.createDirectory(world.resolve("region"));
        Files.writeString(world.resolve("session.lock"), "☃");
        CompoundTag root = new CompoundTag();
        CompoundTag data = new CompoundTag();
        CompoundTag recorded = new CompoundTag();
        recorded.putString("Name", version);
        data.put("Version", recorded);
        root.put("Data", data);
        try (var output = Files.newOutputStream(world.resolve("level.dat"))) {
            new NBTSerializer(true).toStream(new NamedTag("", root), output);
        }
        CompoundTag chunk = new CompoundTag();
        chunk.putInt("DataVersion", 2975);
        chunk.putInt("xPos", 0);
        chunk.putInt("zPos", 0);
        chunk.putString("Status", "full");
        ListTag<CompoundTag> sections = new ListTag<>(CompoundTag.class);
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        sections.add(section);
        chunk.put("sections", sections);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var output = new DeflaterOutputStream(bytes)) { new NBTSerializer(false).toStream(new NamedTag("", chunk), output); }
        try (var file = new RandomAccessFile(world.resolve("region/r.0.0.mca").toFile(), "rw")) {
            file.setLength(12288);
            file.writeInt(513);
            file.seek(8192);
            file.writeInt(bytes.size() + 1);
            file.writeByte(2);
            file.write(bytes.toByteArray());
        }
        return world;
    }
}
