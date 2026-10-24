package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;
import net.querz.nbt.io.NBTSerializer;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RegionScanner {
    /**
     * Maximum chunk offset queried by Minecraft's native height/biome Blender.
     */
    static final int FRONTIER_RADIUS_CHUNKS = 7;

    private static final int CHUNKS_PER_REGION_AXIS = ChunkPositionIndex.CHUNKS_PER_REGION_AXIS;
    private static final int CHUNKS_PER_REGION = ChunkPositionIndex.CHUNKS_PER_REGION;
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_SECTORS = 2;
    private static final int HEADER_BYTES = HEADER_SECTORS * SECTOR_BYTES;
    private static final int EXTERNAL_STREAM_FLAG = 0x80;
    private static final int MAX_INTERNAL_PAYLOAD_BYTES = 1_044_475;
    private static final Pattern REGION_FILE_PATTERN =
        Pattern.compile("^r\\.(-?[0-9]+)\\.(-?[0-9]+)\\.mca$");

    private RegionScanner() {
    }

    public static ProcessingReport process(
        String levelId,
        Path regionDirectory,
        BlendingProfile profile
    ) throws IOException {
        return apply(
            analyze(
                levelId,
                regionDirectory.getParent() == null ? regionDirectory : regionDirectory.getParent(),
                regionDirectory,
                profile,
                ProcessingOptions.DEFAULT,
                ProcessingProgressListener.NO_OP
            ),
            ProcessingProgressListener.NO_OP
        );
    }

    static ProcessingPlan analyze(
        String levelId,
        Path overworldDirectory,
        Path regionDirectory,
        BlendingProfile profile,
        ProcessingOptions options,
        ProcessingProgressListener progressListener
    ) throws IOException {
        Objects.requireNonNull(levelId, "levelId");
        Objects.requireNonNull(overworldDirectory, "overworldDirectory");
        Objects.requireNonNull(regionDirectory, "regionDirectory");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(progressListener, "progressListener");

        long analysisStartNanos = System.nanoTime();
        List<Path> regionFiles = listRegionFiles(regionDirectory);
        ProgressTracker tracker = new ProgressTracker(
            ProcessingPhase.ANALYZING,
            regionFiles.size(),
            ProcessingProgress.UNKNOWN_TOTAL,
            progressListener
        );
        tracker.start();

        List<Callable<AnalyzedRegion>> tasks = new ArrayList<>(regionFiles.size());
        for (Path regionFile : regionFiles) {
            tasks.add(() -> {
                String regionName = regionFile.getFileName().toString();
                tracker.regionStarted(regionName);
                try {
                    AnalyzedRegion result = analyzeRegion(levelId, regionDirectory, regionFile, profile);
                    tracker.regionCompleted(regionName, result.discoveredChunks(), 0);
                    return result;
                } catch (IOException | RuntimeException exception) {
                    tracker.regionStopped(regionName);
                    throw exception;
                }
            });
        }
        List<AnalyzedRegion> analyzedRegions = runParallel(tasks, options.effectiveThreadCount());

        ChunkPositionIndex completeTerrain = new ChunkPositionIndex();
        int discoveredChunks = 0;
        long analyzedRegionBytes = 0L;
        for (AnalyzedRegion region : analyzedRegions) {
            discoveredChunks = Math.addExact(discoveredChunks, region.discoveredChunks());
            analyzedRegionBytes = Math.addExact(analyzedRegionBytes, region.snapshot().size());
            for (int index = region.completeTerrain().nextSetBit(0);
                 index >= 0;
                 index = region.completeTerrain().nextSetBit(index + 1)) {
                completeTerrain.add(region.chunkX(index), region.chunkZ(index));
            }
        }
        ChunkPositionIndex frontier = completeTerrain.selectSquareFrontier(FRONTIER_RADIUS_CHUNKS);

        int affectedChunks = 0;
        int affectedRegions = 0;
        int alreadyPreparedChunks = 0;
        List<RegionSelection> selections = new ArrayList<>(analyzedRegions.size());
        for (AnalyzedRegion region : analyzedRegions) {
            BitSet regionFrontier = new BitSet(CHUNKS_PER_REGION);
            BitSet candidates = new BitSet(CHUNKS_PER_REGION);
            int regionAffectedChunks = 0;
            for (int index = region.presentChunks().nextSetBit(0);
                 index >= 0;
                 index = region.presentChunks().nextSetBit(index + 1)) {
                boolean frontierChunk = frontier.contains(region.chunkX(index), region.chunkZ(index));
                if (frontierChunk) {
                    regionFrontier.set(index);
                }
                if (!frontierChunk && !region.unsafeMarkers().get(index)) {
                    continue;
                }
                candidates.set(index);
                ChunkModification modification = region.modifications()[index];
                if (modification.changed()) {
                    affectedChunks = Math.addExact(affectedChunks, 1);
                    regionAffectedChunks++;
                } else if (modification == ChunkModification.ALREADY_PREPARED) {
                    alreadyPreparedChunks = Math.addExact(alreadyPreparedChunks, 1);
                }
            }
            if (regionAffectedChunks > 0) {
                affectedRegions++;
            }
            selections.add(new RegionSelection(region, regionFrontier, candidates));
        }

        int chunksLeftToSelect = options.selectedChunkCount(affectedChunks);
        int selectedChunks = chunksLeftToSelect;
        int selectedRegions = 0;
        long selectedRegionBytes = 0L;
        List<PlannedRegion> plannedRegions = new ArrayList<>(selections.size());
        for (RegionSelection selection : selections) {
            BitSet selected = new BitSet(CHUNKS_PER_REGION);
            for (int index = selection.candidates().nextSetBit(0);
                 index >= 0 && chunksLeftToSelect > 0;
                 index = selection.candidates().nextSetBit(index + 1)) {
                if (selection.region().modifications()[index].changed()) {
                    selected.set(index);
                    chunksLeftToSelect--;
                }
            }
            if (!selected.isEmpty()) {
                selectedRegions++;
                selectedRegionBytes = Math.addExact(selectedRegionBytes, selection.region().snapshot().size());
            }
            plannedRegions.add(new PlannedRegion(
                selection.region().regionFile(),
                selection.region().regionX(),
                selection.region().regionZ(),
                selection.region().snapshot(),
                selection.region().discoveredChunks(),
                selection.frontierChunks(),
                selection.candidates(),
                selected,
                selection.region().modifications()
            ));
        }

        Duration analysisDuration = Duration.ofNanos(Math.max(0L, System.nanoTime() - analysisStartNanos));
        ProcessingPlanSummary summary = new ProcessingPlanSummary(
            regionFiles.size(),
            discoveredChunks,
            frontier.size(),
            affectedChunks,
            selectedChunks,
            affectedChunks - selectedChunks,
            affectedRegions,
            selectedRegions,
            analyzedRegionBytes,
            selectedRegionBytes
        );
        emitAnalysisChunkEvents(plannedRegions, progressListener, analysisDuration);
        tracker.finish(
            ProcessingPhase.ANALYSIS_COMPLETE,
            discoveredChunks,
            discoveredChunks,
            affectedChunks
        );
        return new ProcessingPlan(
            levelId,
            overworldDirectory,
            regionDirectory,
            profile,
            options,
            summary,
            analysisDuration,
            plannedRegions,
            alreadyPreparedChunks
        );
    }

    static ProcessingReport apply(
        ProcessingPlan plan,
        ProcessingProgressListener progressListener
    ) throws IOException {
        return apply(plan, progressListener, RegionScanner::moveReplacing);
    }

    static ProcessingReport apply(
        ProcessingPlan plan,
        ProcessingProgressListener progressListener,
        MoveOperation commitMove
    ) throws IOException {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(progressListener, "progressListener");
        Objects.requireNonNull(commitMove, "commitMove");

        validateRegionSet(plan);
        ProgressTracker validationTracker = new ProgressTracker(
            ProcessingPhase.VALIDATING,
            plan.regions().size(),
            plan.summary().discoveredChunks(),
            progressListener
        );
        validationTracker.setAffectedChunks(plan.summary().affectedChunks());
        validationTracker.start();
        List<Callable<Void>> validationTasks = new ArrayList<>(plan.regions().size());
        for (PlannedRegion region : plan.regions()) {
            validationTasks.add(() -> {
                String regionName = region.regionFile().getFileName().toString();
                validationTracker.regionStarted(regionName);
                try {
                    validateRegionSnapshot(plan.regionDirectory(), region);
                    validationTracker.regionCompleted(regionName, region.discoveredChunks(), 0);
                    return null;
                } catch (IOException | RuntimeException exception) {
                    validationTracker.regionStopped(regionName);
                    throw exception;
                }
            });
        }
        runParallel(validationTasks, plan.options().effectiveThreadCount());

        List<PlannedRegion> selectedRegions = plan.regions().stream()
            .filter(region -> region.selectedChunkCount() > 0)
            .toList();
        ProgressTracker applicationTracker = new ProgressTracker(
            ProcessingPhase.APPLYING,
            selectedRegions.size(),
            plan.summary().selectedChunks(),
            progressListener
        );
        applicationTracker.setAffectedChunks(plan.summary().affectedChunks());
        applicationTracker.start();
        List<Callable<RegionApplicationResult>> applicationTasks = new ArrayList<>(selectedRegions.size());
        for (PlannedRegion region : selectedRegions) {
            applicationTasks.add(() -> {
                String regionName = region.regionFile().getFileName().toString();
                applicationTracker.regionStarted(regionName);
                try {
                    RegionApplicationResult result = processRegionFile(
                        plan,
                        region,
                        applicationTracker,
                        commitMove
                    );
                    applicationTracker.regionCompleted(regionName, 0, 0);
                    return result;
                } catch (IOException | RuntimeException exception) {
                    applicationTracker.regionStopped(regionName);
                    throw exception;
                }
            });
        }
        List<RegionApplicationResult> results = runParallel(
            applicationTasks,
            plan.options().effectiveThreadCount()
        );

        int preparedChunks = 0;
        int repairedChunks = 0;
        for (RegionApplicationResult result : results) {
            preparedChunks = Math.addExact(preparedChunks, result.preparedChunks());
            repairedChunks = Math.addExact(repairedChunks, result.repairedChunks());
        }
        int skippedChunks = plan.summary().discoveredChunks()
            - preparedChunks
            - repairedChunks
            - plan.alreadyPreparedChunks();
        ProcessingReport report = new ProcessingReport(
            plan.summary().regionFiles(),
            plan.summary().discoveredChunks(),
            plan.summary().frontierChunks(),
            preparedChunks,
            repairedChunks,
            skippedChunks,
            plan.alreadyPreparedChunks()
        );
        applicationTracker.finish(
            ProcessingPhase.COMPLETE,
            plan.summary().selectedChunks(),
            plan.summary().selectedChunks(),
            plan.summary().affectedChunks()
        );
        return report;
    }

    private static List<Path> listRegionFiles(Path regionDirectory) throws IOException {
        if (!Files.exists(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var paths = Files.list(regionDirectory)) {
            return paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> REGION_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
    }

    private static AnalyzedRegion analyzeRegion(
        String levelId,
        Path regionDirectory,
        Path regionFile,
        BlendingProfile profile
    ) throws IOException {
        RegionCoordinates coordinates = parseRegionCoordinates(regionFile);
        BasicFileAttributes attributesBefore = readRegionAttributes(regionFile);
        BitSet presentChunks = new BitSet(CHUNKS_PER_REGION);
        BitSet completeTerrain = new BitSet(CHUNKS_PER_REGION);
        BitSet unsafeMarkers = new BitSet(CHUNKS_PER_REGION);
        ChunkModification[] modifications = new ChunkModification[CHUNKS_PER_REGION];
        Map<Integer, byte[]> externalChunkDigests = new TreeMap<>();
        int discoveredChunks = 0;
        byte[] headerDigest;

        try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r")) {
            RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
            headerDigest = header.digest();
            for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                RegionLocation location = header.locations()[index];
                if (location == null) {
                    continue;
                }

                presentChunks.set(index);
                discoveredChunks++;
                int chunkX = coordinates.chunkX(index);
                int chunkZ = coordinates.chunkZ(index);
                ChunkRecord record;
                try {
                    record = readChunkRecord(source, regionDirectory, location, chunkX, chunkZ);
                } catch (IOException exception) {
                    throw withChunkContext("read", levelId, regionFile, chunkX, chunkZ, exception);
                }
                if (record.external()) {
                    externalChunkDigests.put(index, sha256(record.payload()));
                }

                ChunkProcessor.AnalyzedChunk analyzedChunk;
                try (InputStream payload = new ByteArrayInputStream(record.payload());
                     InputStream decompressed = record.compression().decompress(payload)) {
                    analyzedChunk = ChunkProcessor.readAndAnalyze(decompressed, chunkX, chunkZ, profile);
                } catch (IOException | RuntimeException exception) {
                    throw withChunkContext("analyze", levelId, regionFile, chunkX, chunkZ, exception);
                }
                if (analyzedChunk.inspection().completeTerrain()) {
                    completeTerrain.set(index);
                }
                if (analyzedChunk.inspection().unsafeMarker()) {
                    unsafeMarkers.set(index);
                }
                modifications[index] = analyzedChunk.modification();
            }
        }

        BasicFileAttributes attributesAfter = readRegionAttributes(regionFile);
        if (!sameAttributes(attributesBefore, attributesAfter)) {
            throw stalePlan(regionFile, "the region changed while it was being analyzed");
        }
        RegionFileSnapshot snapshot = new RegionFileSnapshot(
            attributesAfter.size(),
            attributesAfter.lastModifiedTime().toMillis(),
            headerDigest,
            externalChunkDigests
        );
        return new AnalyzedRegion(
            regionFile,
            coordinates.regionX(),
            coordinates.regionZ(),
            coordinates.chunkXOffset(),
            coordinates.chunkZOffset(),
            snapshot,
            presentChunks,
            completeTerrain,
            unsafeMarkers,
            modifications,
            discoveredChunks
        );
    }

    private static RegionApplicationResult processRegionFile(
        ProcessingPlan plan,
        PlannedRegion plannedRegion,
        ProgressTracker applicationTracker,
        MoveOperation commitMove
    ) throws IOException {
        Path regionDirectory = plan.regionDirectory();
        Path regionFile = plannedRegion.regionFile();
        Path temporaryRegion = Files.createTempFile(
            regionDirectory,
            regionFile.getFileName().toString() + ".forceblend-",
            ".tmp"
        );
        List<PendingExternalChunk> pendingExternalChunks = new ArrayList<>();
        int preparedChunks = 0;
        int repairedChunks = 0;

        try {
            try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r");
                 RandomAccessFile destination = new RandomAccessFile(temporaryRegion.toFile(), "rw")) {
                ensureSnapshotAttributes(regionFile, plannedRegion.snapshot());
                RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
                if (!plannedRegion.snapshot().headerMatches(header.digest())) {
                    throw stalePlan(regionFile, "the Anvil header changed before application");
                }
                destination.setLength(HEADER_BYTES);
                int destinationSector = HEADER_SECTORS;

                for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                    RegionLocation location = header.locations()[index];
                    if (location == null) {
                        continue;
                    }

                    int chunkX = Math.addExact(Math.multiplyExact(plannedRegion.regionX(), CHUNKS_PER_REGION_AXIS), index & 31);
                    int chunkZ = Math.addExact(Math.multiplyExact(plannedRegion.regionZ(), CHUNKS_PER_REGION_AXIS), index >>> 5);
                    ChunkRecord record;
                    try {
                        record = readChunkRecord(source, regionDirectory, location, chunkX, chunkZ);
                    } catch (IOException exception) {
                        throw withChunkContext("read", plan.levelId(), regionFile, chunkX, chunkZ, exception);
                    }

                    byte[] outputPayload = record.payload();
                    boolean changed = false;
                    if (plannedRegion.isSelected(index)) {
                        ChunkProcessor.ProcessedChunk processed;
                        try (InputStream payload = new ByteArrayInputStream(record.payload());
                             InputStream decompressed = record.compression().decompress(payload)) {
                            processed = ChunkProcessor.readAndProcess(
                                decompressed,
                                chunkX,
                                chunkZ,
                                plan.profile()
                            );
                        } catch (IOException | RuntimeException exception) {
                            throw withChunkContext("process", plan.levelId(), regionFile, chunkX, chunkZ, exception);
                        }
                        ChunkModification expected = plannedRegion.modification(index);
                        if (!processed.changed() || processed.modification() != expected) {
                            throw stalePlan(
                                regionFile,
                                "chunk [" + chunkX + ", " + chunkZ + "] no longer matches the analyzed outcome"
                            );
                        }
                        outputPayload = encodeChunk(processed, record.compression());
                        changed = true;
                        if (processed.modification() == ChunkModification.PREPARED) {
                            preparedChunks++;
                        } else if (processed.modification() == ChunkModification.REPAIRED_UNSAFE_MARKER) {
                            repairedChunks++;
                        }
                        applicationTracker.chunkProcessed(
                            regionFile.getFileName().toString(),
                            chunkX,
                            chunkZ,
                            processed.modification()
                        );
                    }

                    boolean external = record.external() || outputPayload.length > MAX_INTERNAL_PAYLOAD_BYTES;
                    int sectors = external ? 1 : sectorsFor(outputPayload.length + 5);
                    if (sectors > 255) {
                        throw withChunkContext(
                            "write",
                            plan.levelId(),
                            regionFile,
                            chunkX,
                            chunkZ,
                            new IOException("Chunk requires more than 255 internal sectors")
                        );
                    }

                    destination.seek((long) destinationSector * SECTOR_BYTES);
                    if (external) {
                        destination.writeInt(1);
                        destination.writeByte(record.compression().id() | EXTERNAL_STREAM_FLAG);
                        if (changed) {
                            pendingExternalChunks.add(stageExternalChunk(
                                regionDirectory,
                                chunkX,
                                chunkZ,
                                outputPayload
                            ));
                        }
                    } else {
                        destination.writeInt(outputPayload.length + 1);
                        destination.writeByte(record.compression().id());
                        destination.write(outputPayload);
                    }

                    writeHeaderEntry(
                        destination,
                        index,
                        destinationSector,
                        sectors,
                        changed ? currentTimestamp() : header.timestamps()[index]
                    );
                    destinationSector += sectors;
                }

                destination.setLength((long) destinationSector * SECTOR_BYTES);
                destination.getChannel().force(true);
            }

            validateRegionSnapshot(regionDirectory, plannedRegion);
            commitRegionTransaction(temporaryRegion, regionFile, pendingExternalChunks, commitMove);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temporaryRegion);
            for (PendingExternalChunk externalChunk : pendingExternalChunks) {
                Files.deleteIfExists(externalChunk.temporary());
            }
            if (exception instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Failed to process region file " + regionFile, exception);
        }

        return new RegionApplicationResult(preparedChunks, repairedChunks);
    }

    private static void emitAnalysisChunkEvents(
        List<PlannedRegion> plannedRegions,
        ProcessingProgressListener listener,
        Duration elapsed
    ) {
        if (!detailedEventsEnabled(listener)) {
            return;
        }
        for (PlannedRegion region : plannedRegions) {
            int chunkXOffset = Math.multiplyExact(region.regionX(), CHUNKS_PER_REGION_AXIS);
            int chunkZOffset = Math.multiplyExact(region.regionZ(), CHUNKS_PER_REGION_AXIS);
            BitSet presentChunks = region.presentChunks();
            for (int index = presentChunks.nextSetBit(0);
                 index >= 0;
                 index = presentChunks.nextSetBit(index + 1)) {
                ChunkModification outcome = region.isCandidate(index)
                    ? region.modification(index)
                    : ChunkModification.SKIPPED_NOT_SELECTED;
                notifyChunkEvent(
                    listener,
                    new ChunkProcessingEvent(
                        ProcessingPhase.ANALYSIS_COMPLETE,
                        region.regionFile().getFileName().toString(),
                        chunkXOffset + (index & 31),
                        chunkZOffset + (index >>> 5),
                        outcome,
                        region.isSelected(index),
                        elapsed
                    )
                );
            }
        }
    }

    private static boolean detailedEventsEnabled(ProcessingProgressListener listener) {
        try {
            return listener.detailedEventsEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static void notifyChunkEvent(
        ProcessingProgressListener listener,
        ChunkProcessingEvent event
    ) {
        try {
            listener.onChunkEvent(event);
        } catch (RuntimeException ignored) {
            // Diagnostics are observational and must not change processing behavior.
        }
    }

    private static void validateRegionSet(ProcessingPlan plan) throws IOException {
        List<Path> currentRegionFiles = listRegionFiles(plan.regionDirectory());
        List<Path> plannedRegionFiles = plan.regions().stream().map(PlannedRegion::regionFile).toList();
        if (!currentRegionFiles.equals(plannedRegionFiles)) {
            throw stalePlan(plan.regionDirectory(), "the set of region files changed after analysis");
        }
    }

    private static void validateRegionSnapshot(Path regionDirectory, PlannedRegion plannedRegion) throws IOException {
        Path regionFile = plannedRegion.regionFile();
        ensureSnapshotAttributes(regionFile, plannedRegion.snapshot());
        try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r")) {
            RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
            if (!plannedRegion.snapshot().headerMatches(header.digest())) {
                throw stalePlan(regionFile, "the Anvil header changed after analysis");
            }
        }

        int chunkXOffset = Math.multiplyExact(plannedRegion.regionX(), CHUNKS_PER_REGION_AXIS);
        int chunkZOffset = Math.multiplyExact(plannedRegion.regionZ(), CHUNKS_PER_REGION_AXIS);
        for (Map.Entry<Integer, byte[]> entry : plannedRegion.snapshot().externalChunkDigests().entrySet()) {
            int index = entry.getKey();
            Path externalPath = externalChunkPath(
                regionDirectory,
                chunkXOffset + (index & 31),
                chunkZOffset + (index >>> 5)
            );
            if (Files.isSymbolicLink(externalPath)
                || !Files.isRegularFile(externalPath, LinkOption.NOFOLLOW_LINKS)
                || !Arrays.equals(entry.getValue(), sha256(externalPath))) {
                throw stalePlan(regionFile, "an external chunk payload changed after analysis");
            }
        }
        ensureSnapshotAttributes(regionFile, plannedRegion.snapshot());
    }

    private static void ensureSnapshotAttributes(Path regionFile, RegionFileSnapshot snapshot) throws IOException {
        if (Files.isSymbolicLink(regionFile)
            || !Files.isRegularFile(regionFile, LinkOption.NOFOLLOW_LINKS)) {
            throw stalePlan(regionFile, "the region file is missing or unsafe");
        }
        BasicFileAttributes attributes = readRegionAttributes(regionFile);
        if (attributes.size() != snapshot.size()
            || attributes.lastModifiedTime().toMillis() != snapshot.lastModifiedMillis()) {
            throw stalePlan(regionFile, "the region file changed after analysis");
        }
    }

    private static BasicFileAttributes readRegionAttributes(Path regionFile) throws IOException {
        return Files.readAttributes(regionFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean sameAttributes(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size()
            && first.lastModifiedTime().equals(second.lastModifiedTime())
            && Objects.equals(first.fileKey(), second.fileKey());
    }

    private static RegionCoordinates parseRegionCoordinates(Path regionFile) throws IOException {
        Matcher matcher = REGION_FILE_PATTERN.matcher(regionFile.getFileName().toString());
        if (!matcher.matches()) {
            throw new IOException("Invalid region file name " + regionFile);
        }
        int regionX = parseRegionCoordinate(matcher.group(1), regionFile);
        int regionZ = parseRegionCoordinate(matcher.group(2), regionFile);
        try {
            return new RegionCoordinates(
                regionX,
                regionZ,
                Math.multiplyExact(regionX, CHUNKS_PER_REGION_AXIS),
                Math.multiplyExact(regionZ, CHUNKS_PER_REGION_AXIS)
            );
        } catch (ArithmeticException exception) {
            throw new IOException("Region coordinates exceed the supported chunk range: " + regionFile, exception);
        }
    }

    private static ChunkRecord readChunkRecord(
        RandomAccessFile source,
        Path regionDirectory,
        RegionLocation location,
        int chunkX,
        int chunkZ
    ) throws IOException {
        source.seek((long) location.sectorOffset() * SECTOR_BYTES);
        int length = source.readInt();
        if (length < 1 || length > location.sectorCount() * SECTOR_BYTES - Integer.BYTES) {
            throw new IOException("Invalid chunk stream length " + length);
        }

        int storedCompression = source.readUnsignedByte();
        RegionCompression compression = RegionCompression.fromStoredByte(storedCompression);
        boolean external = (storedCompression & EXTERNAL_STREAM_FLAG) != 0;
        if (external) {
            if (length != 1) {
                throw new IOException("External chunk marker has an invalid length " + length);
            }
            Path externalPath = externalChunkPath(regionDirectory, chunkX, chunkZ);
            if (Files.isSymbolicLink(externalPath)
                || !Files.isRegularFile(externalPath, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("External chunk payload is missing or unsafe: " + externalPath);
            }
            return new ChunkRecord(compression, true, Files.readAllBytes(externalPath));
        }

        byte[] payload = new byte[length - 1];
        source.readFully(payload);
        return new ChunkRecord(compression, false, payload);
    }

    private static byte[] encodeChunk(
        ChunkProcessor.ProcessedChunk processed,
        RegionCompression compression
    ) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        try (OutputStream compressed = compression.compress(bytes)) {
            new NBTSerializer(false).toStream(processed.namedTag(), compressed);
        }
        return bytes.toByteArray();
    }

    private static PendingExternalChunk stageExternalChunk(
        Path regionDirectory,
        int chunkX,
        int chunkZ,
        byte[] payload
    ) throws IOException {
        Path target = externalChunkPath(regionDirectory, chunkX, chunkZ);
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Refusing to replace symbolic link for external chunk: " + target);
        }
        Path temporary = Files.createTempFile(
            regionDirectory,
            "c." + chunkX + "." + chunkZ + ".forceblend-",
            ".tmp"
        );
        Files.write(temporary, payload);
        return new PendingExternalChunk(temporary, target);
    }

    private static Path externalChunkPath(Path regionDirectory, int chunkX, int chunkZ) {
        return regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc");
    }

    private static void writeHeaderEntry(
        RandomAccessFile destination,
        int index,
        int sectorOffset,
        int sectorCount,
        int timestamp
    ) throws IOException {
        if (sectorOffset > 0xFF_FFFF) {
            throw new IOException("Region file exceeds the maximum Anvil sector offset");
        }
        destination.seek((long) index * Integer.BYTES);
        destination.writeInt(sectorOffset << 8 | sectorCount);
        destination.seek(SECTOR_BYTES + (long) index * Integer.BYTES);
        destination.writeInt(timestamp);
    }

    private static int parseRegionCoordinate(String value, Path regionFile) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid coordinates in region file " + regionFile, exception);
        }
    }

    private static int sectorsFor(int bytes) {
        return (bytes + SECTOR_BYTES - 1) / SECTOR_BYTES;
    }

    private static int currentTimestamp() {
        return Math.toIntExact(Instant.now().getEpochSecond());
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Commits a region and its external chunk sidecars as one recoverable operation. Anvil requires
     * sidecars to exist before the new region header becomes visible, so originals are snapshotted
     * first and restored if any move fails. Hard links keep the common rollback path constant-time;
     * filesystems without hard-link support fall back to copying the original.
     */
    private static void commitRegionTransaction(
        Path temporaryRegion,
        Path regionFile,
        List<PendingExternalChunk> pendingExternalChunks,
        MoveOperation commitMove
    ) throws IOException {
        if (pendingExternalChunks.isEmpty()) {
            commitMove.move(temporaryRegion, regionFile);
            return;
        }

        List<RollbackSnapshot> snapshots = new ArrayList<>(pendingExternalChunks.size() + 1);
        boolean commitStarted = false;
        try {
            snapshots.add(createRollbackSnapshot(regionFile));
            for (PendingExternalChunk externalChunk : pendingExternalChunks) {
                snapshots.add(createRollbackSnapshot(externalChunk.target()));
            }

            commitStarted = true;
            for (PendingExternalChunk externalChunk : pendingExternalChunks) {
                commitMove.move(externalChunk.temporary(), externalChunk.target());
            }
            commitMove.move(temporaryRegion, regionFile);
        } catch (IOException | RuntimeException failure) {
            if (commitStarted) {
                IOException rollbackFailure = restoreSnapshots(snapshots);
                if (rollbackFailure != null) {
                    failure.addSuppressed(rollbackFailure);
                }
            } else {
                discardSnapshots(snapshots);
            }
            throw failure;
        }
        discardSnapshots(snapshots);
    }

    private static RollbackSnapshot createRollbackSnapshot(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return new RollbackSnapshot(target, null, false);
        }
        if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Cannot snapshot an unsafe transaction target: " + target);
        }

        Path directory = target.getParent();
        if (directory == null) {
            throw new IOException("Transaction target has no parent directory: " + target);
        }
        Path snapshot = Files.createTempFile(
            directory,
            target.getFileName().toString() + ".forceblend-rollback-",
            ".tmp"
        );
        try {
            Files.delete(snapshot);
            try {
                Files.createLink(snapshot, target);
            } catch (IOException | UnsupportedOperationException linkFailure) {
                try {
                    Files.copy(target, snapshot, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException copyFailure) {
                    copyFailure.addSuppressed(linkFailure);
                    throw copyFailure;
                }
            }
            return new RollbackSnapshot(target, snapshot, true);
        } catch (IOException | RuntimeException failure) {
            try {
                Files.deleteIfExists(snapshot);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    private static IOException restoreSnapshots(List<RollbackSnapshot> snapshots) {
        IOException rollbackFailure = null;
        for (int index = snapshots.size() - 1; index >= 0; index--) {
            RollbackSnapshot snapshot = snapshots.get(index);
            try {
                snapshot.restore();
            } catch (IOException | RuntimeException failure) {
                IOException contextualFailure = failure instanceof IOException ioFailure
                    ? ioFailure
                    : new IOException("Could not restore " + snapshot.target(), failure);
                if (rollbackFailure == null) {
                    rollbackFailure = new IOException("ForceBlend could not fully roll back a region transaction");
                }
                rollbackFailure.addSuppressed(contextualFailure);
            }
        }
        return rollbackFailure;
    }

    private static void discardSnapshots(List<RollbackSnapshot> snapshots) {
        for (RollbackSnapshot snapshot : snapshots) {
            if (snapshot.rollbackFile() == null) {
                continue;
            }
            try {
                Files.deleteIfExists(snapshot.rollbackFile());
            } catch (IOException ignored) {
                snapshot.rollbackFile().toFile().deleteOnExit();
            }
        }
    }

    private static byte[] sha256(byte[] bytes) {
        MessageDigest digest = newSha256();
        return digest.digest(bytes);
    }

    private static byte[] sha256(Path path) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            byte[] buffer = new byte[64 * 1024];
            int length;
            while ((length = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        return digest.digest();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static IOException stalePlan(Path path, String detail) {
        return new IOException("ForceBlend processing plan is stale for " + path + ": " + detail);
    }

    private static IOException withChunkContext(
        String operation,
        String levelId,
        Path regionFile,
        int chunkX,
        int chunkZ,
        Exception cause
    ) {
        return new IOException(
            "Failed to " + operation + " chunk [" + chunkX + ", " + chunkZ + "] in "
                + regionFile + " for world '" + levelId + "'",
            cause
        );
    }

    private static <T> List<T> runParallel(List<? extends Callable<T>> tasks, int requestedThreads)
        throws IOException {
        if (tasks.isEmpty()) {
            return List.of();
        }
        int workerCount = Math.max(1, Math.min(requestedThreads, tasks.size()));
        AtomicInteger workerNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(workerCount, runnable -> {
            Thread thread = new Thread(runnable, "forceblend-worker-" + workerNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(task));
            }
            List<T> results = new ArrayList<>(Collections.nCopies(tasks.size(), null));
            for (int index = 0; index < futures.size(); index++) {
                try {
                    results.set(index, futures.get(index).get());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    cancelAll(futures);
                    throw new IOException("ForceBlend processing was interrupted", exception);
                } catch (ExecutionException exception) {
                    cancelAll(futures);
                    Throwable cause = exception.getCause();
                    if (cause instanceof IOException ioException) {
                        throw ioException;
                    }
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new IOException("ForceBlend worker failed", cause);
                }
            }
            return Collections.unmodifiableList(results);
        } finally {
            executor.shutdownNow();
            boolean restoreInterrupt = Thread.interrupted();
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    restoreInterrupt = true;
                }
            }
            if (restoreInterrupt) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void cancelAll(List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
    }

    private record RegionLocation(int sectorOffset, int sectorCount) {
    }

    private record ChunkRecord(RegionCompression compression, boolean external, byte[] payload) {
    }

    private record PendingExternalChunk(Path temporary, Path target) {
    }

    @FunctionalInterface
    interface MoveOperation {
        void move(Path source, Path target) throws IOException;
    }

    private record RollbackSnapshot(Path target, Path rollbackFile, boolean existed) {
        void restore() throws IOException {
            if (existed) {
                moveReplacing(rollbackFile, target);
                return;
            }
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Refusing to remove an unsafe transaction target during rollback: " + target);
            }
            Files.delete(target);
        }
    }

    private record RegionApplicationResult(int preparedChunks, int repairedChunks) {
    }

    private record RegionSelection(AnalyzedRegion region, BitSet frontierChunks, BitSet candidates) {
    }

    private record RegionCoordinates(int regionX, int regionZ, int chunkXOffset, int chunkZOffset) {
        int chunkX(int index) {
            return chunkXOffset + (index & 31);
        }

        int chunkZ(int index) {
            return chunkZOffset + (index >>> 5);
        }
    }

    private record AnalyzedRegion(
        Path regionFile,
        int regionX,
        int regionZ,
        int chunkXOffset,
        int chunkZOffset,
        RegionFileSnapshot snapshot,
        BitSet presentChunks,
        BitSet completeTerrain,
        BitSet unsafeMarkers,
        ChunkModification[] modifications,
        int discoveredChunks
    ) {
        int chunkX(int index) {
            return chunkXOffset + (index & 31);
        }

        int chunkZ(int index) {
            return chunkZOffset + (index >>> 5);
        }
    }

    private static final class ProgressTracker {
        private static final long PROGRESS_EMIT_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(50L);

        private final ProcessingPhase phase;
        private final int totalRegions;
        private final int totalChunks;
        private final ProcessingProgressListener listener;
        private final boolean detailedEventsEnabled;
        private final long startNanos = System.nanoTime();
        private final Set<String> activeRegions = new TreeSet<>();
        private final Map<String, Long> regionStartNanos = new HashMap<>();
        private int completedRegions;
        private int completedChunks;
        private int affectedChunks;
        private long lastProgressEmitNanos;

        private ProgressTracker(
            ProcessingPhase phase,
            int totalRegions,
            int totalChunks,
            ProcessingProgressListener listener
        ) {
            this.phase = phase;
            this.totalRegions = totalRegions;
            this.totalChunks = totalChunks;
            this.listener = listener;
            this.detailedEventsEnabled = RegionScanner.detailedEventsEnabled(listener);
        }

        synchronized void setAffectedChunks(int affectedChunks) {
            this.affectedChunks = affectedChunks;
        }

        synchronized void start() {
            emit(phase, totalChunks);
        }

        synchronized void regionStarted(String regionName) {
            activeRegions.add(regionName);
            regionStartNanos.put(regionName, System.nanoTime());
            emit(phase, totalChunks);
            notifyRegionEvent(regionName, RegionEventState.STARTED, 0);
        }

        synchronized void regionCompleted(String regionName, int chunks, int affected) {
            activeRegions.remove(regionName);
            completedRegions++;
            completedChunks = Math.addExact(completedChunks, chunks);
            affectedChunks = Math.addExact(affectedChunks, affected);
            emit(phase, totalChunks);
            notifyRegionEvent(regionName, RegionEventState.COMPLETED, chunks);
        }

        synchronized void regionStopped(String regionName) {
            activeRegions.remove(regionName);
            emit(phase, totalChunks);
            notifyRegionEvent(regionName, RegionEventState.FAILED, 0);
        }

        synchronized void chunkProcessed(
            String regionName,
            int chunkX,
            int chunkZ,
            ChunkModification outcome
        ) {
            completedChunks = Math.addExact(completedChunks, 1);
            long now = System.nanoTime();
            if (completedChunks == totalChunks || now - lastProgressEmitNanos >= PROGRESS_EMIT_INTERVAL_NANOS) {
                emit(phase, totalChunks);
            }
            if (detailedEventsEnabled) {
                notifyChunkEvent(
                    listener,
                    new ChunkProcessingEvent(
                        phase,
                        regionName,
                        chunkX,
                        chunkZ,
                        outcome,
                        true,
                        Duration.ofNanos(Math.max(0L, now - startNanos))
                    )
                );
            }
        }

        synchronized void finish(
            ProcessingPhase finalPhase,
            int finalCompletedChunks,
            int finalTotalChunks,
            int finalAffectedChunks
        ) {
            completedRegions = totalRegions;
            completedChunks = finalCompletedChunks;
            affectedChunks = finalAffectedChunks;
            activeRegions.clear();
            emit(finalPhase, finalTotalChunks);
        }

        private void emit(ProcessingPhase eventPhase, int eventTotalChunks) {
            lastProgressEmitNanos = System.nanoTime();
            ProcessingProgress progress = new ProcessingProgress(
                eventPhase,
                completedRegions,
                totalRegions,
                completedChunks,
                eventTotalChunks,
                affectedChunks,
                List.copyOf(activeRegions),
                Duration.ofNanos(Math.max(0L, System.nanoTime() - startNanos))
            );
            try {
                listener.onProgress(progress);
            } catch (RuntimeException ignored) {
                // Progress is observational and must never compromise a transactional region write.
            }
        }

        private void notifyRegionEvent(String regionName, RegionEventState state, int chunks) {
            Long startedNanos = state == RegionEventState.STARTED
                ? regionStartNanos.get(regionName)
                : regionStartNanos.remove(regionName);
            if (!detailedEventsEnabled) {
                return;
            }
            long durationNanos = state == RegionEventState.STARTED || startedNanos == null
                ? 0L
                : Math.max(0L, System.nanoTime() - startedNanos);
            try {
                listener.onRegionEvent(new ProcessingRegionEvent(
                    phase,
                    state,
                    regionName,
                    chunks,
                    Duration.ofNanos(durationNanos)
                ));
            } catch (RuntimeException ignored) {
                // Diagnostics are observational and must not change processing behavior.
            }
        }
    }

    private record RegionHeader(RegionLocation[] locations, int[] timestamps, byte[] digest) {
        private static RegionHeader readAndValidate(RandomAccessFile source, Path regionFile) throws IOException {
            long fileLength = source.length();
            if (fileLength < HEADER_BYTES) {
                throw new EOFException("Region file is shorter than the 8 KiB Anvil header: " + regionFile);
            }

            long sectorsLong = (fileLength + SECTOR_BYTES - 1) / SECTOR_BYTES;
            if (sectorsLong > Integer.MAX_VALUE) {
                throw new IOException("Region file is too large to validate: " + regionFile);
            }
            int fileSectors = (int) sectorsLong;
            BitSet usedSectors = new BitSet(fileSectors);
            usedSectors.set(0, HEADER_SECTORS);

            byte[] headerBytes = new byte[HEADER_BYTES];
            source.seek(0L);
            source.readFully(headerBytes);
            RegionLocation[] locations = new RegionLocation[CHUNKS_PER_REGION];
            int[] timestamps = new int[CHUNKS_PER_REGION];
            try (DataInputStream header = new DataInputStream(new ByteArrayInputStream(headerBytes))) {
                for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                    int packed = header.readInt();
                    int offset = packed >>> 8;
                    int count = packed & 0xFF;
                    if (offset == 0 && count == 0) {
                        continue;
                    }
                    if (offset < HEADER_SECTORS || count == 0 || (long) offset + count > fileSectors) {
                        throw new IOException("Invalid region location entry " + index + " in " + regionFile);
                    }
                    int overlap = usedSectors.nextSetBit(offset);
                    if (overlap >= 0 && overlap < offset + count) {
                        throw new IOException("Overlapping chunk sectors at entry " + index + " in " + regionFile);
                    }
                    usedSectors.set(offset, offset + count);
                    locations[index] = new RegionLocation(offset, count);
                }
                for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                    timestamps[index] = header.readInt();
                }
            }
            return new RegionHeader(locations, timestamps, sha256(headerBytes));
        }
    }
}
