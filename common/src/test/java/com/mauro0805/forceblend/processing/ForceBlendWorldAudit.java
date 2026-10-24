package com.mauro0805.forceblend.processing;

import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.ByteTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only diagnostic for the chunk statuses and blending markers in a real world.
 */
public final class ForceBlendWorldAudit {
    private static final int SECTOR_BYTES = 4096;
    private static final Pattern REGION_FILE = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
    private static final Path MODERN_OVERWORLD_PATH = Path.of("dimensions", "minecraft", "overworld");

    private ForceBlendWorldAudit() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Provide the world or Overworld dimension directory");
        }

        Path suppliedPath = Path.of(args[0]).toAbsolutePath().normalize();
        Path modernOverworld = suppliedPath.resolve(MODERN_OVERWORLD_PATH);
        Path overworld = Files.isDirectory(modernOverworld.resolve("region")) ? modernOverworld : suppliedPath;
        Path regionDirectory = overworld.resolve("region");
        if (!Files.isDirectory(regionDirectory)) {
            throw new IOException("Overworld region directory not found under " + suppliedPath);
        }

        Map<String, Integer> statuses = new TreeMap<>();
        Map<String, Integer> markers = new TreeMap<>();
        Map<String, Integer> statusMarkers = new TreeMap<>();
        ChunkPositionIndex completeTerrain = new ChunkPositionIndex();
        int[] chunks = {0};
        try (var paths = Files.list(regionDirectory)) {
            for (Path region : paths.filter(Files::isRegularFile).sorted().toList()) {
                Matcher matcher = REGION_FILE.matcher(region.getFileName().toString());
                if (!matcher.matches()) {
                    continue;
                }
                int regionX = Integer.parseInt(matcher.group(1));
                int regionZ = Integer.parseInt(matcher.group(2));
                auditRegion(
                    regionDirectory,
                    region,
                    regionX,
                    regionZ,
                    statuses,
                    markers,
                    statusMarkers,
                    completeTerrain,
                    chunks
                );
            }
        }

        System.out.println("Chunks: " + chunks[0]);
        System.out.println("Statuses: " + statuses);
        System.out.println("Blending markers: " + markers);
        System.out.println("Status/marker combinations: " + statusMarkers);
        System.out.println(
            "Complete terrain frontier (radius " + RegionScanner.FRONTIER_RADIUS_CHUNKS + "): "
                + completeTerrain.selectSquareFrontier(RegionScanner.FRONTIER_RADIUS_CHUNKS).size()
        );
    }

    private static void auditRegion(
        Path regionDirectory,
        Path region,
        int regionX,
        int regionZ,
        Map<String, Integer> statuses,
        Map<String, Integer> markers,
        Map<String, Integer> statusMarkers,
        ChunkPositionIndex completeTerrain,
        int[] chunks
    ) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "r")) {
            for (int index = 0; index < 1024; index++) {
                file.seek((long) index * 4);
                int packedLocation = file.readInt();
                int sectorOffset = packedLocation >>> 8;
                if (sectorOffset == 0) {
                    continue;
                }

                int chunkX = Math.addExact(Math.multiplyExact(regionX, 32), index & 31);
                int chunkZ = Math.addExact(Math.multiplyExact(regionZ, 32), index >>> 5);
                file.seek((long) sectorOffset * SECTOR_BYTES);
                int storedLength = file.readInt();
                int storedCompression = file.readUnsignedByte();
                boolean external = (storedCompression & 0x80) != 0;
                byte[] payload;
                if (external) {
                    payload = Files.readAllBytes(regionDirectory.resolve("c." + chunkX + "." + chunkZ + ".mcc"));
                } else {
                    payload = new byte[storedLength - 1];
                    file.readFully(payload);
                }

                RegionCompression compression = RegionCompression.fromStoredByte(storedCompression);
                CompoundTag chunkTag;
                try (InputStream decompressed = compression.decompress(new ByteArrayInputStream(payload))) {
                    NamedTag namedTag = new NBTDeserializer(false).fromStream(decompressed);
                    chunkTag = (CompoundTag) namedTag.getTag();
                }

                chunks[0]++;
                String status = chunkTag.getString("Status");
                statuses.merge(status.isBlank() ? "<missing>" : status, 1, Integer::sum);
                String marker = markerKind(chunkTag);
                markers.merge(marker, 1, Integer::sum);
                statusMarkers.merge((status.isBlank() ? "<missing>" : status) + " / " + marker, 1, Integer::sum);
                if (ChunkStatusPolicy.isEligibleForBlending(chunkTag)) {
                    completeTerrain.add(chunkX, chunkZ);
                }
                if (chunkX == 7 && chunkZ == 10) {
                    System.out.printf(
                        "Crash chunk [7,10]: DataVersion=%d, Status=%s, marker=%s, sections=%d%n",
                        chunkTag.getInt("DataVersion"),
                        status,
                        markerKind(chunkTag),
                        chunkTag.getListTag("sections") == null ? 0 : chunkTag.getListTag("sections").size()
                    );
                }
            }
        }
    }

    private static String markerKind(CompoundTag chunkTag) {
        CompoundTag marker = chunkTag.getCompoundTag(ChunkModifier.BLENDING_DATA_KEY);
        if (marker == null) {
            return "absent";
        }
        Tag<?> oldNoise = marker.get("old_noise");
        if (oldNoise instanceof ByteTag value) {
            return value.asByte() == 0 ? "old_noise=false" : "old_noise=true";
        }
        if (marker.containsKey(ChunkModifier.MIN_SECTION_KEY)
            && marker.containsKey(ChunkModifier.MAX_SECTION_KEY)) {
            return "section_range";
        }
        return "unknown";
    }
}
