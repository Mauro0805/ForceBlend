package com.mauro0805.forceblend.processing;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4Factory;
import net.jpountz.xxhash.XXHashFactory;

import java.io.*;
import java.util.zip.*;

enum RegionCompression {
    NONE(0),
    GZIP(1),
    ZLIB(2),
    UNCOMPRESSED(3),
    LZ4(4);

    private static final int LZ4_BLOCK_SIZE = 65_536;
    private static final int LZ4_CHECKSUM_SEED = 0x9747B28C;
    private static final LZ4Factory SAFE_JAVA_LZ4 = LZ4Factory.safeInstance();
    private static final XXHashFactory SAFE_JAVA_XXHASH = XXHashFactory.safeInstance();

    private final int id;

    RegionCompression(int id) {
        this.id = id;
    }

    static RegionCompression fromStoredByte(int storedByte) throws IOException {
        int id = storedByte & 0x7F;
        for (RegionCompression compression : values()) {
            if (compression.id == id) {
                return compression;
            }
        }
        throw new IOException("Unsupported Anvil compression type " + id);
    }

    // lz4-pure-java exposes its checksum adapter through a Closeable whose close() is a no-op.
    @SuppressWarnings("resource")
    private static Checksum newLz4Checksum() {
        return SAFE_JAVA_XXHASH.newStreamingHash32(LZ4_CHECKSUM_SEED).asChecksum();
    }

    int id() {
        return id;
    }

    InputStream decompress(InputStream input) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        return switch (this) {
            case NONE, UNCOMPRESSED -> buffered;
            case GZIP -> new GZIPInputStream(buffered);
            case ZLIB -> new InflaterInputStream(buffered);
            case LZ4 -> new LZ4BlockInputStream(
                buffered,
                SAFE_JAVA_LZ4.fastDecompressor(),
                newLz4Checksum(),
                true
            );
        };
    }

    OutputStream compress(OutputStream output) throws IOException {
        BufferedOutputStream buffered = new BufferedOutputStream(output);
        return switch (this) {
            case NONE, UNCOMPRESSED -> buffered;
            case GZIP -> new GZIPOutputStream(buffered);
            case ZLIB -> new DeflaterOutputStream(buffered);
            case LZ4 -> new LZ4BlockOutputStream(
                buffered,
                LZ4_BLOCK_SIZE,
                SAFE_JAVA_LZ4.fastCompressor(),
                newLz4Checksum(),
                false
            );
        };
    }
}
