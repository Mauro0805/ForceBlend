package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;
import net.querz.nbt.io.NBTSerializer;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
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
        List<Path> regionFiles;
        try (var paths = Files.list(regionDirectory)) {
            regionFiles = paths
                .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                .filter(path -> REGION_FILE_PATTERN.matcher(path.getFileName().toString()).matches())
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }

        ScanPlan plan = inspectTopology(levelId, regionDirectory, regionFiles, profile);

        ProcessingReport report = ProcessingReport.EMPTY;
        for (Path regionFile : regionFiles) {
            report = report.add(processRegionFile(
                levelId,
                regionDirectory,
                regionFile,
                profile,
                plan.frontierChunks(),
                plan.unsafeMarkers()
            ));
        }
        return report;
    }

    private static ProcessingReport processRegionFile(
        String levelId,
        Path regionDirectory,
        Path regionFile,
        BlendingProfile profile,
        ChunkPositionIndex frontierChunks,
        ChunkPositionIndex unsafeMarkers
    ) throws IOException {
        Matcher matcher = REGION_FILE_PATTERN.matcher(regionFile.getFileName().toString());
        if (!matcher.matches()) {
            return ProcessingReport.EMPTY;
        }

        int regionX = parseRegionCoordinate(matcher.group(1), regionFile);
        int regionZ = parseRegionCoordinate(matcher.group(2), regionFile);
        final int chunkXOffset;
        final int chunkZOffset;
        try {
            chunkXOffset = Math.multiplyExact(regionX, CHUNKS_PER_REGION_AXIS);
            chunkZOffset = Math.multiplyExact(regionZ, CHUNKS_PER_REGION_AXIS);
        } catch (ArithmeticException exception) {
            throw new IOException("Region coordinates exceed the supported chunk range: " + regionFile, exception);
        }

        ProcessingReport unchangedReport = inspectWithoutWriting(
            levelId,
            regionDirectory,
            regionFile,
            profile,
            frontierChunks,
            unsafeMarkers,
            chunkXOffset,
            chunkZOffset
        );
        if (unchangedReport != null) {
            return unchangedReport;
        }

        Path temporaryRegion = Files.createTempFile(
            regionDirectory,
            regionFile.getFileName().toString() + ".forceblend-",
            ".tmp"
        );
        List<PendingExternalChunk> pendingExternalChunks = new ArrayList<>();
        int discovered = 0;
        int frontier = 0;
        int prepared = 0;
        int repaired = 0;
        int skipped = 0;
        int alreadyPrepared = 0;

        try {
            try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r");
                 RandomAccessFile destination = new RandomAccessFile(temporaryRegion.toFile(), "rw")) {
                RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
                destination.setLength(HEADER_BYTES);
                int destinationSector = HEADER_SECTORS;

                for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                    RegionLocation location = header.locations()[index];
                    if (location == null) {
                        continue;
                    }

                    discovered++;
                    int localX = index & 31;
                    int localZ = index >>> 5;
                    int chunkX = chunkXOffset + localX;
                    int chunkZ = chunkZOffset + localZ;

                    boolean frontierChunk = frontierChunks.contains(chunkX, chunkZ);
                    boolean repairChunk = unsafeMarkers.contains(chunkX, chunkZ);
                    if (frontierChunk) {
                        frontier++;
                    }

                    ChunkRecord record;
                    try {
                        record = readChunkRecord(source, regionDirectory, location, chunkX, chunkZ);
                    } catch (IOException exception) {
                        throw withChunkContext("read", levelId, regionFile, chunkX, chunkZ, exception);
                    }

                    ChunkProcessor.ProcessedChunk processed = null;
                    ChunkModification modification = ChunkModification.SKIPPED_NOT_SELECTED;
                    if (frontierChunk || repairChunk) {
                        try (InputStream payload = new ByteArrayInputStream(record.payload());
                             InputStream decompressed = record.compression().decompress(payload)) {
                            processed = ChunkProcessor.readAndProcess(decompressed, chunkX, chunkZ, profile);
                            modification = processed.modification();
                        } catch (IOException | RuntimeException exception) {
                            throw withChunkContext("process", levelId, regionFile, chunkX, chunkZ, exception);
                        }
                    }

                    byte[] outputPayload = record.payload();
                    boolean changed = processed != null && processed.changed();
                    if (changed) {
                        outputPayload = encodeChunk(processed, record.compression());
                    }
                    switch (modification) {
                        case PREPARED -> prepared++;
                        case REPAIRED_UNSAFE_MARKER -> repaired++;
                        case ALREADY_PREPARED -> alreadyPrepared++;
                        case SKIPPED_INCOMPLETE, SKIPPED_NOT_SELECTED, SKIPPED_NATIVE_MIGRATION -> skipped++;
                    }

                    boolean external = record.external() || outputPayload.length > MAX_INTERNAL_PAYLOAD_BYTES;
                    int sectors = external ? 1 : sectorsFor(outputPayload.length + 5);
                    if (sectors > 255) {
                        throw withChunkContext(
                            "write",
                            levelId,
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

            if (prepared == 0 && repaired == 0) {
                Files.deleteIfExists(temporaryRegion);
            } else {
                for (PendingExternalChunk externalChunk : pendingExternalChunks) {
                    moveReplacing(externalChunk.temporary(), externalChunk.target());
                }
                moveReplacing(temporaryRegion, regionFile);
            }
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

        return new ProcessingReport(1, discovered, frontier, prepared, repaired, skipped, alreadyPrepared);
    }

    /**
     * Returns a complete report when a region needs no mutation. If a changed chunk is found,
     * returns {@code null} immediately so the transactional rewrite can restart from the source.
     */
    private static ProcessingReport inspectWithoutWriting(
        String levelId,
        Path regionDirectory,
        Path regionFile,
        BlendingProfile profile,
        ChunkPositionIndex frontierChunks,
        ChunkPositionIndex unsafeMarkers,
        int chunkXOffset,
        int chunkZOffset
    ) throws IOException {
        int discovered = 0;
        int frontier = 0;
        int skipped = 0;
        int alreadyPrepared = 0;

        try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r")) {
            RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
            for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                RegionLocation location = header.locations()[index];
                if (location == null) {
                    continue;
                }

                discovered++;
                int chunkX = chunkXOffset + (index & 31);
                int chunkZ = chunkZOffset + (index >>> 5);

                boolean frontierChunk = frontierChunks.contains(chunkX, chunkZ);
                boolean repairChunk = unsafeMarkers.contains(chunkX, chunkZ);
                if (frontierChunk) {
                    frontier++;
                }
                if (!frontierChunk && !repairChunk) {
                    skipped++;
                    continue;
                }

                ChunkRecord record;
                try {
                    record = readChunkRecord(source, regionDirectory, location, chunkX, chunkZ);
                } catch (IOException exception) {
                    throw withChunkContext("read", levelId, regionFile, chunkX, chunkZ, exception);
                }

                ChunkProcessor.ProcessedChunk processed;
                try (InputStream payload = new ByteArrayInputStream(record.payload());
                     InputStream decompressed = record.compression().decompress(payload)) {
                    processed = ChunkProcessor.readAndProcess(
                        decompressed,
                        chunkX,
                        chunkZ,
                        profile
                    );
                } catch (IOException | RuntimeException exception) {
                    throw withChunkContext("process", levelId, regionFile, chunkX, chunkZ, exception);
                }

                if (processed.changed()) {
                    return null;
                }
                switch (processed.modification()) {
                    case ALREADY_PREPARED -> alreadyPrepared++;
                    case SKIPPED_INCOMPLETE, SKIPPED_NOT_SELECTED, SKIPPED_NATIVE_MIGRATION -> skipped++;
                    case PREPARED, REPAIRED_UNSAFE_MARKER -> throw new IllegalStateException(
                        "Changed chunk was not detected before report aggregation"
                    );
                }
            }
        }

        return new ProcessingReport(1, discovered, frontier, 0, 0, skipped, alreadyPrepared);
    }

    private static ScanPlan inspectTopology(
        String levelId,
        Path regionDirectory,
        List<Path> regionFiles,
        BlendingProfile profile
    ) throws IOException {
        ChunkPositionIndex completeTerrain = new ChunkPositionIndex();
        ChunkPositionIndex unsafeMarkers = new ChunkPositionIndex();

        for (Path regionFile : regionFiles) {
            Matcher matcher = REGION_FILE_PATTERN.matcher(regionFile.getFileName().toString());
            if (!matcher.matches()) {
                continue;
            }
            int regionX = parseRegionCoordinate(matcher.group(1), regionFile);
            int regionZ = parseRegionCoordinate(matcher.group(2), regionFile);
            final int chunkXOffset;
            final int chunkZOffset;
            try {
                chunkXOffset = Math.multiplyExact(regionX, CHUNKS_PER_REGION_AXIS);
                chunkZOffset = Math.multiplyExact(regionZ, CHUNKS_PER_REGION_AXIS);
            } catch (ArithmeticException exception) {
                throw new IOException("Region coordinates exceed the supported chunk range: " + regionFile, exception);
            }

            try (RandomAccessFile source = new RandomAccessFile(regionFile.toFile(), "r")) {
                RegionHeader header = RegionHeader.readAndValidate(source, regionFile);
                for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                    RegionLocation location = header.locations()[index];
                    if (location == null) {
                        continue;
                    }

                    int chunkX = chunkXOffset + (index & 31);
                    int chunkZ = chunkZOffset + (index >>> 5);
                    ChunkRecord record;
                    try {
                        record = readChunkRecord(source, regionDirectory, location, chunkX, chunkZ);
                    } catch (IOException exception) {
                        throw withChunkContext("read", levelId, regionFile, chunkX, chunkZ, exception);
                    }

                    ChunkModifier.ChunkInspection inspection;
                    try (InputStream payload = new ByteArrayInputStream(record.payload());
                         InputStream decompressed = record.compression().decompress(payload)) {
                        inspection = ChunkProcessor.readAndInspect(decompressed, chunkX, chunkZ, profile);
                    } catch (IOException | RuntimeException exception) {
                        throw withChunkContext("inspect", levelId, regionFile, chunkX, chunkZ, exception);
                    }

                    if (inspection.completeTerrain()) {
                        completeTerrain.add(chunkX, chunkZ);
                    }
                    if (inspection.unsafeMarker()) {
                        unsafeMarkers.add(chunkX, chunkZ);
                    }
                }
            }
        }

        return new ScanPlan(
            completeTerrain.selectSquareFrontier(FRONTIER_RADIUS_CHUNKS),
            unsafeMarkers
        );
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

    private record RegionLocation(int sectorOffset, int sectorCount) {
    }

    private record ChunkRecord(RegionCompression compression, boolean external, byte[] payload) {
    }

    private record PendingExternalChunk(Path temporary, Path target) {
    }

    private record ScanPlan(ChunkPositionIndex frontierChunks, ChunkPositionIndex unsafeMarkers) {
    }

    private record RegionHeader(RegionLocation[] locations, int[] timestamps) {
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

            RegionLocation[] locations = new RegionLocation[CHUNKS_PER_REGION];
            int[] timestamps = new int[CHUNKS_PER_REGION];
            source.seek(0);
            for (int index = 0; index < CHUNKS_PER_REGION; index++) {
                int packed = source.readInt();
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
                timestamps[index] = source.readInt();
            }
            return new RegionHeader(locations, timestamps);
        }
    }
}
