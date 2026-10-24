package com.mauro0805.forceblend.processing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.mauro0805.forceblend.version.BlendingProfile;
import com.mauro0805.forceblend.version.SupportedMinecraftVersions;

import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NBTSerializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;

final class ProcessingPlanTest {
    private static final BlendingProfile PROFILE = SupportedMinecraftVersions.profileFor("26.2");

    private static Path createThreeRegionWorld(Path world) throws IOException {
        Files.createDirectories(world);
        writeSingleChunk(world, -32, 0);
        writeSingleChunk(world, 0, 0);
        writeSingleChunk(world, 32, 0);
        return world;
    }

    private static void writeSingleChunk(Path world, int chunkX, int chunkZ) throws IOException {
        Path regionDirectory = Files.createDirectories(world.resolve("region"));
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
            file.seek(8192L);
            file.writeInt(payload.length + 1);
            file.writeByte(RegionCompression.ZLIB.id());
            file.write(payload);
        }
    }

    private static void writeSingleExternalChunk(Path world, int chunkX, int chunkZ) throws IOException {
        writeSingleChunk(world, chunkX, chunkZ);
        Path regionFile = regionFile(world, chunkX, chunkZ);
        int localIndex = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "rw")) {
            file.seek((long) localIndex * Integer.BYTES);
            int packed = file.readInt();
            file.seek((long) (packed >>> 8) * 4096);
            int length = file.readInt();
            int compression = file.readUnsignedByte();
            byte[] payload = new byte[length - 1];
            file.readFully(payload);
            Files.write(externalChunkFile(world, chunkX, chunkZ), payload);
            file.seek((long) (packed >>> 8) * 4096);
            file.writeInt(1);
            file.writeByte(compression | 0x80);
        }
    }

    private static boolean hasExpectedMarker(Path world, int chunkX, int chunkZ) throws IOException {
        int localIndex = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        Path regionFile = world.resolve("region").resolve(
            "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"
        );
        try (RandomAccessFile file = new RandomAccessFile(regionFile.toFile(), "r")) {
            file.seek((long) localIndex * Integer.BYTES);
            int packed = file.readInt();
            file.seek((long) (packed >>> 8) * 4096);
            int length = file.readInt();
            int storedCompression = file.readUnsignedByte();
            RegionCompression compression = RegionCompression.fromStoredByte(storedCompression);
            byte[] payload;
            if ((storedCompression & 0x80) != 0) {
                assertEquals(1, length);
                payload = Files.readAllBytes(externalChunkFile(world, chunkX, chunkZ));
            } else {
                payload = new byte[length - 1];
                file.readFully(payload);
            }
            try (InputStream decompressed = compression.decompress(new ByteArrayInputStream(payload))) {
                CompoundTag tag = (CompoundTag) new NBTDeserializer(false).fromStream(decompressed).getTag();
                return ChunkModifier.hasExpectedBlendingData(tag, PROFILE);
            }
        }
    }

    private static boolean isExternalChunk(Path world, int chunkX, int chunkZ) throws IOException {
        int localIndex = Math.floorMod(chunkX, 32) + Math.floorMod(chunkZ, 32) * 32;
        try (RandomAccessFile file = new RandomAccessFile(regionFile(world, chunkX, chunkZ).toFile(), "r")) {
            file.seek((long) localIndex * Integer.BYTES);
            int packed = file.readInt();
            file.seek((long) (packed >>> 8) * 4096 + Integer.BYTES);
            return (file.readUnsignedByte() & 0x80) != 0;
        }
    }

    private static Path regionFile(Path world, int chunkX, int chunkZ) {
        return world.resolve("region").resolve(
            "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"
        );
    }

    private static Path externalChunkFile(Path world, int chunkX, int chunkZ) {
        return world.resolve("region").resolve("c." + chunkX + "." + chunkZ + ".mcc");
    }

    private static void moveReplacingForTest(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void assertNoForceBlendArtifacts(Path world) throws IOException {
        try (var paths = Files.list(world.resolve("region"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains(".forceblend-")));
        }
    }

    private static List<byte[]> regionBytes(Path world) throws IOException {
        try (var paths = Files.list(world.resolve("region"))) {
            List<byte[]> contents = new ArrayList<>();
            for (Path path : paths.sorted().toList()) {
                contents.add(Files.readAllBytes(path));
            }
            return contents;
        }
    }

    private static void assertRegionBytesEqual(List<byte[]> expected, List<byte[]> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertArrayEquals(expected.get(index), actual.get(index), "Region " + index + " changed");
        }
    }

    @Test
    void analysisIsReadOnlyAndChunkLimitsStayDeterministicWithParallelWorkers(@TempDir Path temporary)
        throws Exception {
        Path parallelWorld = createThreeRegionWorld(temporary.resolve("parallel"));
        Path sequentialWorld = createThreeRegionWorld(temporary.resolve("sequential"));
        List<byte[]> beforeAnalysis = regionBytes(parallelWorld);
        DetailedListener analysisListener = new DetailedListener();

        ProcessingPlan parallelPlan = WorldProcessor.analyze(
            "parallel-plan",
            parallelWorld,
            PROFILE,
            new ProcessingOptions(4, 2),
            analysisListener
        );

        assertRegionBytesEqual(beforeAnalysis, regionBytes(parallelWorld));
        ProcessingPlanSummary summary = parallelPlan.summary();
        assertEquals(3, summary.regionFiles());
        assertEquals(3, summary.discoveredChunks());
        assertEquals(3, summary.frontierChunks());
        assertEquals(3, summary.affectedChunks());
        assertEquals(2, summary.selectedChunks());
        assertEquals(1, summary.deferredChunks());
        assertEquals(3, summary.affectedRegions());
        assertEquals(2, summary.selectedRegions());
        assertTrue(summary.analyzedRegionBytes() > 0);
        assertTrue(summary.selectedRegionBytes() > 0);
        assertTrue(parallelPlan.estimatedApplyDuration().isPresent());
        assertEquals(ProcessingPhase.ANALYZING, analysisListener.progress().get(0).phase());
        ProcessingProgress analysisComplete = analysisListener.progress().get(analysisListener.progress().size() - 1);
        assertEquals(ProcessingPhase.ANALYSIS_COMPLETE, analysisComplete.phase());
        assertEquals(3, analysisComplete.completedChunks());
        assertEquals(3, analysisComplete.affectedChunks());
        assertEquals(1.0, analysisComplete.completionFraction());
        assertEquals(0L, analysisComplete.estimatedRemaining().orElseThrow().toNanos());
        assertEquals(3L, analysisListener.regionEvents().stream()
            .filter(event -> event.phase() == ProcessingPhase.ANALYZING)
            .filter(event -> event.state() == RegionEventState.COMPLETED)
            .count());
        assertEquals(3L, analysisListener.chunkEvents().stream()
            .filter(event -> event.phase() == ProcessingPhase.ANALYSIS_COMPLETE)
            .count());
        assertEquals(2L, analysisListener.chunkEvents().stream()
            .filter(ChunkProcessingEvent::selected)
            .count());

        DetailedListener applicationListener = new DetailedListener();
        ProcessingReport parallelReport = WorldProcessor.apply(parallelPlan, applicationListener);
        assertEquals(2, parallelReport.preparedChunks());
        assertEquals(1, parallelReport.skippedChunks());
        assertTrue(hasExpectedMarker(parallelWorld, -32, 0));
        assertTrue(hasExpectedMarker(parallelWorld, 0, 0));
        assertFalse(hasExpectedMarker(parallelWorld, 32, 0));
        assertTrue(applicationListener.progress().stream()
            .anyMatch(progress -> progress.phase() == ProcessingPhase.VALIDATING));
        assertTrue(applicationListener.progress().stream()
            .anyMatch(progress -> progress.phase() == ProcessingPhase.APPLYING));
        ProcessingProgress complete = applicationListener.progress().get(applicationListener.progress().size() - 1);
        assertEquals(ProcessingPhase.COMPLETE, complete.phase());
        assertEquals(2, complete.completedChunks());
        assertEquals(2, complete.totalChunks());
        assertEquals(0L, complete.estimatedRemaining().orElseThrow().toNanos());
        assertEquals(2L, applicationListener.chunkEvents().stream()
            .filter(event -> event.phase() == ProcessingPhase.APPLYING)
            .count());
        assertEquals(2L, applicationListener.regionEvents().stream()
            .filter(event -> event.phase() == ProcessingPhase.APPLYING)
            .filter(event -> event.state() == RegionEventState.COMPLETED)
            .count());

        ProcessingPlan sequentialPlan = WorldProcessor.analyze(
            "sequential-plan",
            sequentialWorld,
            PROFILE,
            new ProcessingOptions(1, 2)
        );
        assertEquals(parallelPlan.summary(), sequentialPlan.summary());
        WorldProcessor.apply(sequentialPlan);
        assertTrue(hasExpectedMarker(sequentialWorld, -32, 0));
        assertTrue(hasExpectedMarker(sequentialWorld, 0, 0));
        assertFalse(hasExpectedMarker(sequentialWorld, 32, 0));
    }

    @Test
    void applyRejectsAStalePlanBeforeWritingAnyRegion(@TempDir Path worldDirectory) throws Exception {
        Path world = Files.createDirectories(worldDirectory.resolve("world"));
        writeSingleChunk(world, 0, 0);
        ProcessingPlan plan = WorldProcessor.analyze(
            "stale-plan",
            world,
            PROFILE,
            ProcessingOptions.DEFAULT
        );

        Path regionFile = world.resolve("region").resolve("r.0.0.mca");
        Files.write(regionFile, new byte[]{0}, StandardOpenOption.APPEND);
        byte[] changedWorld = Files.readAllBytes(regionFile);

        IOException exception = assertThrows(IOException.class, () -> WorldProcessor.apply(plan));

        assertTrue(exception.getMessage().contains("stale"));
        assertArrayEquals(changedWorld, Files.readAllBytes(regionFile));
        try (var paths = Files.list(world.resolve("region"))) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains(".forceblend-")));
        }
    }

    @Test
    void processesExternalChunksAndKeepsTheirSidecarsConsistent(@TempDir Path temporary) throws Exception {
        Path world = Files.createDirectories(temporary.resolve("external-world"));
        writeSingleExternalChunk(world, 0, 0);

        ProcessingPlan plan = WorldProcessor.analyze(
            "external-happy-path",
            world,
            PROFILE,
            ProcessingOptions.DEFAULT
        );
        ProcessingReport report = WorldProcessor.apply(plan);

        assertEquals(1, report.preparedChunks());
        assertTrue(hasExpectedMarker(world, 0, 0));
        assertTrue(isExternalChunk(world, 0, 0));
        assertNoForceBlendArtifacts(world);
    }

    @Test
    void rejectsAStaleExternalPayloadBeforeWriting(@TempDir Path temporary) throws Exception {
        Path world = Files.createDirectories(temporary.resolve("stale-external-world"));
        writeSingleExternalChunk(world, 0, 0);
        ProcessingPlan plan = WorldProcessor.analyze(
            "stale-external",
            world,
            PROFILE,
            ProcessingOptions.DEFAULT
        );
        Path regionFile = regionFile(world, 0, 0);
        Path externalFile = externalChunkFile(world, 0, 0);
        byte[] regionBefore = Files.readAllBytes(regionFile);
        Files.write(externalFile, new byte[]{42}, StandardOpenOption.APPEND);
        byte[] changedExternal = Files.readAllBytes(externalFile);

        IOException exception = assertThrows(IOException.class, () -> WorldProcessor.apply(plan));

        assertTrue(exception.getMessage().contains("stale"));
        assertArrayEquals(regionBefore, Files.readAllBytes(regionFile));
        assertArrayEquals(changedExternal, Files.readAllBytes(externalFile));
        assertNoForceBlendArtifacts(world);
    }

    @Test
    void rollsBackExternalPayloadWhenTheFinalRegionMoveFails(@TempDir Path temporary) throws Exception {
        Path world = Files.createDirectories(temporary.resolve("rollback-region-world"));
        writeSingleExternalChunk(world, 0, 0);
        ProcessingPlan plan = WorldProcessor.analyze(
            "rollback-region",
            world,
            PROFILE,
            ProcessingOptions.DEFAULT
        );
        Path regionFile = regionFile(world, 0, 0);
        Path externalFile = externalChunkFile(world, 0, 0);
        byte[] regionBefore = Files.readAllBytes(regionFile);
        byte[] externalBefore = Files.readAllBytes(externalFile);
        AtomicInteger committedSidecars = new AtomicInteger();

        IOException exception = assertThrows(IOException.class, () -> RegionScanner.apply(
            plan,
            ProcessingProgressListener.NO_OP,
            (source, target) -> {
                if (target.equals(regionFile)) {
                    // Model the strongest Files.move failure mode: the destination changed even though
                    // the provider reported an IOException and left the final state unspecified.
                    moveReplacingForTest(source, target);
                    throw new IOException("Injected final region move failure");
                }
                moveReplacingForTest(source, target);
                committedSidecars.incrementAndGet();
            }
        ));

        assertTrue(exception.getMessage().contains("Injected"));
        assertEquals(1, committedSidecars.get());
        assertArrayEquals(regionBefore, Files.readAllBytes(regionFile));
        assertArrayEquals(externalBefore, Files.readAllBytes(externalFile));
        assertNoForceBlendArtifacts(world);
    }

    @Test
    void leavesOriginalFilesWhenAnExternalPayloadMoveFails(@TempDir Path temporary) throws Exception {
        Path world = Files.createDirectories(temporary.resolve("rollback-external-world"));
        writeSingleExternalChunk(world, 0, 0);
        ProcessingPlan plan = WorldProcessor.analyze(
            "rollback-external",
            world,
            PROFILE,
            ProcessingOptions.DEFAULT
        );
        Path regionFile = regionFile(world, 0, 0);
        Path externalFile = externalChunkFile(world, 0, 0);
        byte[] regionBefore = Files.readAllBytes(regionFile);
        byte[] externalBefore = Files.readAllBytes(externalFile);

        IOException exception = assertThrows(IOException.class, () -> RegionScanner.apply(
            plan,
            ProcessingProgressListener.NO_OP,
            (source, target) -> {
                if (target.equals(externalFile)) {
                    throw new IOException("Injected external payload move failure");
                }
                moveReplacingForTest(source, target);
            }
        ));

        assertTrue(exception.getMessage().contains("Injected"));
        assertArrayEquals(regionBefore, Files.readAllBytes(regionFile));
        assertArrayEquals(externalBefore, Files.readAllBytes(externalFile));
        assertNoForceBlendArtifacts(world);
    }

    @Test
    void processingOptionsValidateDefaultsAndExplicitLimits() {
        assertEquals(ProcessingOptions.ALL_AVAILABLE_THREADS, ProcessingOptions.DEFAULT.threadCount());
        assertEquals(ProcessingOptions.UNLIMITED_CHUNKS, ProcessingOptions.DEFAULT.maximumChunks());
        assertTrue(ProcessingOptions.DEFAULT.effectiveThreadCount() >= 1);
        assertFalse(ProcessingOptions.DEFAULT.hasChunkLimit());
        assertTrue(new ProcessingOptions(1, 0).hasChunkLimit());
        assertThrows(IllegalArgumentException.class, () -> new ProcessingOptions(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ProcessingOptions(1, -2));
    }

    private static final class DetailedListener implements ProcessingProgressListener {
        private final List<ProcessingProgress> progress = Collections.synchronizedList(new ArrayList<>());
        private final List<ProcessingRegionEvent> regionEvents = Collections.synchronizedList(new ArrayList<>());
        private final List<ChunkProcessingEvent> chunkEvents = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void onProgress(ProcessingProgress progress) {
            this.progress.add(progress);
        }

        @Override
        public boolean detailedEventsEnabled() {
            return true;
        }

        @Override
        public void onRegionEvent(ProcessingRegionEvent event) {
            regionEvents.add(event);
        }

        @Override
        public void onChunkEvent(ChunkProcessingEvent event) {
            chunkEvents.add(event);
        }

        List<ProcessingProgress> progress() {
            return progress;
        }

        List<ProcessingRegionEvent> regionEvents() {
            return regionEvents;
        }

        List<ChunkProcessingEvent> chunkEvents() {
            return chunkEvents;
        }
    }
}
