package com.mauro0805.forceblend.processing;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressionDiagnosticsTest {
    @Test
    void allCodecsRoundTripEmptyAndMultiBlockPayloads() throws IOException {
        byte[] large = new byte[200_000];
        new Random(42).nextBytes(large);
        for (RegionCompression codec : RegionCompression.values()) {
            for (byte[] input : new byte[][]{new byte[0], large}) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (var output = codec.compress(bytes)) {
                    output.write(input);
                }
                try (var decoded = codec.decompress(new ByteArrayInputStream(bytes.toByteArray()))) {
                    assertArrayEquals(input, decoded.readAllBytes(), codec.name());
                }
            }
        }
    }

    @Test
    void externalFlagDoesNotChangeCodec() throws IOException {
        for (RegionCompression codec : RegionCompression.values()) {
            assertEquals(codec, RegionCompression.fromStoredByte(codec.id() | 0x80));
        }
        assertThrows(IOException.class, () -> RegionCompression.fromStoredByte(0x85));
    }

    @Test
    void compressedCodecsRejectInvalidHeaders() {
        for (RegionCompression codec : new RegionCompression[]{
                RegionCompression.GZIP, RegionCompression.ZLIB, RegionCompression.LZ4}) {
            assertThrows(IOException.class, () -> {
                try (var input = codec.decompress(new ByteArrayInputStream(new byte[]{1, 2, 3}))) {
                    input.readAllBytes();
                }
            }, codec.name());
        }
    }
}
