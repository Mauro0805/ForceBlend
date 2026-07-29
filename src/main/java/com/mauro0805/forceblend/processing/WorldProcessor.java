package com.mauro0805.forceblend.processing;

import com.mauro0805.forceblend.version.BlendingProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class WorldProcessor {
    private static final String REGION_DIRECTORY_NAME = "region";

    private WorldProcessor() {
    }

    public static ProcessingReport process(
        String levelId,
        Path overworldDirectory,
        BlendingProfile profile
    ) throws IOException {
        Path normalizedOverworld = overworldDirectory.toAbsolutePath().normalize();
        Path regionDirectory = normalizedOverworld.resolve(REGION_DIRECTORY_NAME).normalize();
        if (!regionDirectory.startsWith(normalizedOverworld)) {
            throw new IOException("Region directory resolves outside the selected Overworld: " + regionDirectory);
        }
        if (Files.isSymbolicLink(regionDirectory)) {
            throw new IOException("Symbolic links are not allowed for the region directory: " + regionDirectory);
        }
        if (!Files.exists(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return ProcessingReport.EMPTY;
        }
        if (!Files.isDirectory(regionDirectory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("The Overworld region path is not a directory: " + regionDirectory);
        }

        return RegionScanner.process(levelId, regionDirectory, profile);
    }
}
