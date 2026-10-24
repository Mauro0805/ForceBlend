package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;
import net.querz.nbt.io.NBTDeserializer;
import net.querz.nbt.io.NamedTag;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.IntTag;
import net.querz.nbt.tag.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

final class ChunkProcessor {
    private ChunkProcessor() {
    }

    static ProcessedChunk readAndProcess(
        InputStream decompressedInput,
        int expectedX,
        int expectedZ,
        BlendingProfile profile
    ) throws IOException {
        Objects.requireNonNull(decompressedInput, "decompressedInput");

        NamedTag namedTag = new NBTDeserializer(false).fromStream(decompressedInput);
        if (!(namedTag.getTag() instanceof CompoundTag chunkTag)) {
            throw new IOException("Chunk NBT root is not a compound tag");
        }
        validateStoredPosition(chunkTag, expectedX, expectedZ);

        final ChunkModification modification;
        try {
            modification = ChunkModifier.apply(chunkTag, profile);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
        return new ProcessedChunk(namedTag, modification);
    }

    static ChunkModifier.ChunkInspection readAndInspect(
        InputStream decompressedInput,
        int expectedX,
        int expectedZ,
        BlendingProfile profile
    ) throws IOException {
        Objects.requireNonNull(decompressedInput, "decompressedInput");

        NamedTag namedTag = new NBTDeserializer(false).fromStream(decompressedInput);
        if (!(namedTag.getTag() instanceof CompoundTag chunkTag)) {
            throw new IOException("Chunk NBT root is not a compound tag");
        }
        validateStoredPosition(chunkTag, expectedX, expectedZ);
        try {
            return ChunkModifier.inspect(chunkTag, profile);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    /**
     * Predicts the exact outcome without writing anything to disk. The parsed NBT is intentionally
     * mutated in memory and then discarded so analysis and application share one decision path.
     */
    static AnalyzedChunk readAndAnalyze(
        InputStream decompressedInput,
        int expectedX,
        int expectedZ,
        BlendingProfile profile
    ) throws IOException {
        Objects.requireNonNull(decompressedInput, "decompressedInput");

        NamedTag namedTag = new NBTDeserializer(false).fromStream(decompressedInput);
        if (!(namedTag.getTag() instanceof CompoundTag chunkTag)) {
            throw new IOException("Chunk NBT root is not a compound tag");
        }
        validateStoredPosition(chunkTag, expectedX, expectedZ);
        try {
            ChunkModifier.ChunkInspection inspection = ChunkModifier.inspect(chunkTag, profile);
            ChunkModification modification = ChunkModifier.apply(chunkTag, profile);
            return new AnalyzedChunk(inspection, modification);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }
    }

    private static void validateStoredPosition(CompoundTag chunkTag, int expectedX, int expectedZ) throws IOException {
        Tag<?> rawXPos = chunkTag.get("xPos");
        Tag<?> rawZPos = chunkTag.get("zPos");
        if (!(rawXPos instanceof IntTag xPos) || !(rawZPos instanceof IntTag zPos)) {
            throw new IOException("Chunk is missing integer xPos/zPos coordinates");
        }
        if (xPos.asInt() != expectedX || zPos.asInt() != expectedZ) {
            throw new IOException(
                "Chunk is stored at the wrong position: expected [" + expectedX + ", " + expectedZ
                    + "], found [" + xPos.asInt() + ", " + zPos.asInt() + "]"
            );
        }
    }

    record ProcessedChunk(NamedTag namedTag, ChunkModification modification) {
        boolean changed() {
            return modification.changed();
        }
    }

    record AnalyzedChunk(ChunkModifier.ChunkInspection inspection, ChunkModification modification) {
    }
}
